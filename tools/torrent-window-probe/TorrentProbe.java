
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import android.content.ContextWrapper;
import kotlin.coroutines.*;
import kotlin.ResultKt;
import org.libtorrent4j.*;
import org.libtorrent4j.alerts.*;
import com.pelican.comictv.tv.*;

public class TorrentProbe {
  static volatile TvTorrentSession current;
  static final int PIECE=524288;
  static final long SIZE=1516383963L;
  public static void main(String[] args) { try { run(args); } catch(Throwable t) { t.printStackTrace(System.out);System.exit(1); } }
  public static void run(String[] args) throws Exception {
    Thread main=Thread.currentThread();
    Thread watchdog=new Thread(()->{try {while(true){Thread.sleep(10000);System.out.println("WATCH stats="+(current==null?"none":current.stats())+" main="+Arrays.toString(main.getStackTrace()));}}catch(InterruptedException ignored){} });watchdog.setDaemon(true);watchdog.start();
    File root=new File(args[0]);
    System.setProperty("libtorrent4j.jni.path",new File(root,"libtorrent4j.so").getAbsolutePath());
    File seedDir=new File(root,"seed"); seedDir.mkdir();
    File cache=new File(root,"cache");cache.mkdir();
    File seedFile=new File(seedDir,"synthetic.mp4");
    try(RandomAccessFile out=new RandomAccessFile(seedFile,"rw")) {
      out.setLength(SIZE);
      for(int p=0;p<(SIZE+PIECE-1)/PIECE;p++){out.seek(p*(long)PIECE);out.writeInt(p);}
    }
    TorrentInfo info=new TorrentInfo(new File(root,"synthetic.torrent"));
    SettingsPack settings=new SettingsPack().listenInterfaces("127.0.0.1:37891");
    settings.setEnableDht(false);settings.setEnableLsd(false);
    SessionManager seed=new SessionManager();
    seed.addListener(new AlertListener(){public int[] types(){return new int[]{AlertType.PEER_DISCONNECTED.swig()};} public void alert(Alert<?> alert){if(alert instanceof PeerDisconnectedAlert)System.out.println("SEED_DISCONNECT "+((PeerDisconnectedAlert)alert).error().getValue()+" "+((PeerDisconnectedAlert)alert).error().getMessage());}});SessionParams seedParams=new SessionParams(settings);seedParams.setPosixDiskIO();seed.start(seedParams);
    AddTorrentParams params=new AddTorrentParams();params.setTorrentInfo(info);params.setSavePath(seedDir.getAbsolutePath());
    params.setFlags(params.getFlags().and_(TorrentFlags.AUTO_MANAGED.or_(TorrentFlags.PAUSED).inv()).or_(TorrentFlags.SEED_MODE));
    seed.swig().async_add_torrent(params.swig());
    TvTorrentSession torrent=new TvTorrentSession(new ContextWrapper(null){@Override public File getCacheDir(){return cache;}},1,false);
    current=torrent;
    CompletableFuture<TvTorrentSession> ready=new CompletableFuture<>();
    TvStream stream=new TvStream("synthetic","","magnet:?xt=urn:btih:"+info.infoHash().toHex()+"&x.pe=127.0.0.1%3A37891","",null,Collections.emptyList(),"",Collections.emptyMap(),"",Collections.emptyList());
    try {
      Object prepared=torrent.prepare(stream,new Continuation<TvTorrentSession>(){
        public CoroutineContext getContext(){return EmptyCoroutineContext.INSTANCE;}
        public void resumeWith(Object result){try {ResultKt.throwOnFailure(result);ready.complete((TvTorrentSession)result);}catch(Throwable t){ready.completeExceptionally(t);}}
      });
      if(prepared!=kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED())ready.complete((TvTorrentSession)prepared);
      Field hf=TvTorrentSession.class.getDeclaredField("handle");hf.setAccessible(true);
      long deadline=System.currentTimeMillis()+60000;
      while(!ready.isDone()&&System.currentTimeMillis()<deadline){
        TorrentHandle handle=(TorrentHandle)hf.get(torrent);
        if(handle!=null&&handle.isValid())handle.swig().connect_peer(new TcpEndpoint("127.0.0.1",37891).swig());
        Thread.sleep(250);
      }
      ready.get(5,TimeUnit.SECONDS);
      System.out.println("PREPARED size="+torrent.getSize()+" usable="+cache.getUsableSpace());
      byte[] marker=new byte[4];
      int last=(int)((SIZE-1)/PIECE);
      // Tail, beginning, every forward window, and a final backwards seek. Each read uses the actual app class.
      List<Integer> reads=new ArrayList<>();reads.add(last);reads.add(0);
      for(int p=47;p<=last;p+=48){reads.add(p);if(p<last)reads.add(p+1);}
      reads.add(last);reads.add(3);
      for(int p:reads){
        long started=System.currentTimeMillis();
        int n=torrent.read(p*(long)PIECE,marker,0,4,()->false);
        int actual=((marker[0]&255)<<24)|((marker[1]&255)<<16)|((marker[2]&255)<<8)|(marker[3]&255);
        if(n!=4||actual!=p)throw new AssertionError("Wrong bytes piece="+p+" got="+actual);
        System.out.println("READ piece="+p+" ms="+(System.currentTimeMillis()-started)+" usable="+cache.getUsableSpace());
      }
      System.out.println("PASS native actual-session large-file bounded-window seek bytes");
    } finally {torrent.close();seed.stop();seedFile.delete();System.out.flush();}
    System.exit(0);
  }
}

