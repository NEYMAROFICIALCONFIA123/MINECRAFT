"""Fatia o jogador recortado em pecas (cabeca, tronco, bracos, pernas...)
Gera: assets/pecas/*.png + pecas.js (dados p/ o jogo) + previews p/ conferir.
Uso: python3 tools_fatiar.py   (rodar dentro de ragdoll-sandbox/)
"""
import json, os
import numpy as np, cv2
from PIL import Image, ImageDraw

os.makedirs('assets/pecas', exist_ok=True)
img = np.array(Image.open('assets/jogador-recortado.png').convert('RGBA')).copy()
H, W = img.shape[:2]
rgb, alpha = img[:,:,:3].astype(int), img[:,:,3]

# 1) apaga o pedaco do pilar amarelo que vazou entre o braco direito e o tronco
R,G,B = rgb[:,:,0], rgb[:,:,1], rgb[:,:,2]
amarelo = (R>165)&(G>120)&(B<105)&((R-B)>75)&((G-B)>40)
zona = np.zeros((H,W), bool); zona[520:700, 545:612] = True
alpha = np.where(amarelo & zona, 0, alpha).astype(np.uint8)
# 2) erode 2px p/ tirar a franja azulada da borda
alpha = cv2.erode(alpha, np.ones((3,3), np.uint8), iterations=2)
img[:,:,3] = alpha
Image.fromarray(img).save('assets/jogador-recortado.png')
print('limpeza ok')

# poligonos (coords da foto 944x1118) e pivô (junta com a peca-mae)
PECAS = {
 'cabeca':  dict(pivot=(475,448), poly=[(398,330),(420,282),(478,268),(525,290),(542,340),(528,400),(512,432),(514,464),(430,464),(422,430),(398,405),(388,365)]),
 'tronco':  dict(pivot=(478,615), poly=[(368,448),(430,428),(530,428),(578,448),(596,500),(584,560),(592,640),(596,720),(606,790),(600,840),(372,854),(362,780),(356,700),(348,620),(342,545),(350,485)]),
 'bracoE':  dict(pivot=(360,495), poly=[(352,458),(392,478),(378,540),(360,600),(368,660),(392,694),(352,702),(300,684),(268,640),(262,580),(272,520),(300,478)]),
 'bracoD':  dict(pivot=(578,488), poly=[(548,448),(600,452),(630,482),(638,524),(620,552),(640,590),(658,620),(628,642),(602,612),(592,560),(576,516),(556,486)]),
 'anteD':   dict(pivot=(644,602), poly=[(614,576),(658,578),(678,620),(680,690),(672,755),(652,794),(618,780),(606,720),(608,650)]),
 'quadril': dict(pivot=(490,815), poly=[(368,790),(606,778),(632,830),(624,894),(540,902),(520,872),(492,896),(430,906),(372,896),(360,840)]),
 'coxaE':   dict(pivot=(440,845), poly=[(378,820),(500,812),(512,880),(498,950),(492,994),(420,998),(402,940),(388,880)]),
 'canelaE': dict(pivot=(462,975), poly=[(424,940),(498,938),(503,1010),(495,1070),(500,1118),(408,1118),(412,1060),(420,990)]),
 'coxaD':   dict(pivot=(548,838), poly=[(492,812),(620,806),(640,870),(630,940),(618,984),(528,986),(516,920),(500,862)]),
 'canelaD': dict(pivot=(578,972), poly=[(530,940),(616,938),(620,1000),(622,1060),(630,1118),(556,1118),(552,1040),(540,985)]),
}
ORDEM = ['bracoE','canelaE','coxaE','canelaD','coxaD','quadril','tronco','cabeca','bracoD','anteD']

dados = {}
base = Image.fromarray(img)
for nome, p in PECAS.items():
    mask = Image.new('L', (W,H), 0)
    ImageDraw.Draw(mask).polygon(p['poly'], fill=255)
    m = np.minimum(np.array(mask), img[:,:,3])
    peca = img.copy(); peca[:,:,3] = m
    ys, xs = np.where(m > 10)
    x0,x1,y0,y1 = int(xs.min()), int(xs.max())+1, int(ys.min()), int(ys.max())+1
    Image.fromarray(peca[y0:y1, x0:x1]).save(f'assets/pecas/{nome}.png')
    dados[nome] = dict(file=f'assets/pecas/{nome}.png', bbox=[x0,y0,x1-x0,y1-y0],
                       centro=[(x0+x1)/2,(y0+y1)/2], pivot=list(p['pivot']))
    print(nome, dados[nome])

with open('pecas.js','w') as f:
    f.write('// gerado por tools_fatiar.py — dados das pecas (coords da foto 944x1118)\n')
    f.write('window.PECAS_DADOS = ' + json.dumps(dados, indent=1) + ';\n')
    f.write('window.ORDEM_DESENHO = ' + json.dumps(ORDEM) + ';\n')

# preview: remonta na pose original p/ conferir buracos
prev = Image.new('RGBA', (W,H), (30,30,40,255))
for nome in ORDEM:
    d = dados[nome]; x0,y0 = d['bbox'][0], d['bbox'][1]
    prev.alpha_composite(Image.open(d['file']), (x0,y0))
prev.convert('RGB').save('assets/preview-remontado.jpg', quality=88)
print('preview -> assets/preview-remontado.jpg')
