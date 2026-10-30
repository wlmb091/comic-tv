package com.pelican.comictv.transfer

object UploadPage {
    val html = """<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Enviar quadrinhos · Comic TV</title><style>
*{box-sizing:border-box}body{margin:0;background:#101113;color:#f5f3ef;font:17px system-ui,sans-serif;line-height:1.5}
main{max-width:600px;margin:8vh auto;padding:28px}small{color:#ffbd59;letter-spacing:.15em}h1{font-size:36px;line-height:1.15;margin:24px 0 16px}
p{color:#b8babe}label{display:block;padding:24px;background:#1c1e21;border:1px solid #444;border-radius:12px;margin:24px 0 16px}
input{display:block;width:100%;margin-top:14px;font:inherit}button{background:#ffbd59;color:#15120c;font:700 17px system-ui;border:0;border-radius:8px;padding:16px 24px;cursor:pointer}
button:disabled{opacity:.5;cursor:wait}button:focus-visible,input:focus-visible{outline:3px solid white;outline-offset:4px}
progress{width:100%;height:10px;accent-color:#ffbd59;margin-top:24px}#status{white-space:pre-wrap;overflow-wrap:anywhere}
</style></head><body><main><small>COMIC TV</small><h1>Enviar quadrinhos</h1>
<p>Adicione arquivos à biblioteca da sua TV. Mantenha a tela de transferência aberta na TV durante o envio.</p>
<form id="form"><label>Escolha CBZ, ZIP, CBR, RAR, 7z, CB7, PDF ou EPUB<input id="files" type="file" multiple required></label>
<p>Envie HQs individuais ou uma coleção ZIP/RAR com até 100 HQs em arquivos separados. A TV importa cada edição e mostra o resumo. O formato é reconhecido pelo conteúdo. Arquivos protegidos por senha ou DRM não são suportados.</p>
<p>Até 1 GiB por arquivo. Os arquivos selecionados serão enviados um de cada vez.</p><button id="send">Enviar para a TV</button></form>
<progress id="progress" max="100" value="0" aria-label="Progresso do envio"></progress><p id="status" role="status" aria-live="polite">Pronto para enviar.</p>
<script>
const form=document.getElementById('form'),files=document.getElementById('files'),button=document.getElementById('send'),status=document.getElementById('status'),progress=document.getElementById('progress');
const base=location.pathname.replace(/\/$/,'');let active=false;
function upload(file){return new Promise((resolve,reject)=>{const xhr=new XMLHttpRequest();xhr.open('POST',base+'/upload?name='+encodeURIComponent(file.name));xhr.setRequestHeader('Content-Type','application/octet-stream');xhr.timeout=15*60*1000;
xhr.upload.onprogress=e=>{if(e.lengthComputable){progress.value=e.loaded/e.total*100;status.textContent=e.loaded===e.total?'Validando '+file.name+' na TV…':'Enviando '+file.name+' · '+Math.round(progress.value)+'%';}};
xhr.onload=()=>xhr.status===200?resolve():reject(new Error(xhr.responseText||'A TV recusou o arquivo.'));xhr.onerror=()=>reject(new Error('Conexão interrompida. Verifique a rede e a tela da TV.'));xhr.ontimeout=()=>reject(new Error('O envio demorou demais. Inicie outra sessão na TV.'));xhr.send(file);});}
form.addEventListener('submit',async e=>{e.preventDefault();if(active)return;const selected=Array.from(files.files);if(!selected.length)return;
if(selected.some(f=>!(/\.(cbz|zip|cbr|rar|7z|cb7|pdf|epub)$/i.test(f.name))||f.size===0||f.size>1073741824)){status.textContent='Selecione CBZ, ZIP, CBR, RAR, 7z, CB7, PDF ou EPUB de até 1 GiB, com conteúdo.';return;}
active=true;button.disabled=true;files.disabled=true;let completed=0;
try{for(const file of selected){progress.value=0;status.textContent='Preparando '+file.name+'…';await upload(file);completed++;}status.textContent=completed+' envio(s) processado(s). Confira o resumo da importação na TV.';}
catch(error){status.textContent=error.message+'\nArquivos concluídos neste envio: '+completed;}
finally{active=false;button.disabled=false;files.disabled=false;}});
setInterval(async()=>{try{const response=await fetch(base+'/status',{cache:'no-store'});if(response.ok&&!active){button.disabled=false;}else if(!response.ok&&!active){button.disabled=true;status.textContent='Sessão encerrada. Inicie uma nova sessão na TV e abra o novo endereço.';}}catch{if(!active){button.disabled=true;status.textContent='TV indisponível. Confira a rede e reabra o endereço mostrado na TV.';}}},3000);
</script></main></body></html>"""
}
