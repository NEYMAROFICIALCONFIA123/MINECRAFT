"""Fatia o jogador recortado em 16 pecas articuladas (pescoco, peito, barriga,
bracos com antebracos e mao, pernas com pes...) p/ o ragdoll ter 15 juntas.
Gera: assets/pecas/*.png + pecas.js (dados p/ o jogo) + previews p/ conferir.
Uso: python3 tools_fatiar.py   (rodar dentro de ragdoll-sandbox/)
"""
import json, os, glob
import numpy as np, cv2
from PIL import Image, ImageDraw

os.makedirs('assets/pecas', exist_ok=True)
img = np.array(Image.open('assets/jogador-recortado.png').convert('RGBA')).copy()
H, W = img.shape[:2]

# limpeza (so na 1a vez — senao a erosao repetida ia "comendo" as pecas)
if not os.path.exists('assets/.limpo'):
    rgb, alpha = img[:,:,:3].astype(int), img[:,:,3]
    R,G,B = rgb[:,:,0], rgb[:,:,1], rgb[:,:,2]
    amarelo = (R>165)&(G>120)&(B<105)&((R-B)>75)&((G-B)>40)  # pilar vazado no recorte
    zona = np.zeros((H,W), bool); zona[520:700, 545:612] = True
    alpha = np.where(amarelo & zona, 0, alpha).astype(np.uint8)
    alpha = cv2.erode(alpha, np.ones((3,3), np.uint8), iterations=2)  # tira franja azul
    img[:,:,3] = alpha
    Image.fromarray(img).save('assets/jogador-recortado.png')
    open('assets/.limpo','w').close()
    print('limpeza ok')
else:
    print('limpeza ja feita antes, pulando')

# poligonos (coords da foto 944x1118) e pivot = junta com a peca-mae
# hierarquia: peito -> pescoco -> cabeca | peito -> bracoE -> anteE
#             peito -> bracoD -> anteD -> maoD
#             peito -> barriga -> quadril -> coxa -> canela -> pe
PECAS = {
 'cabeca':  dict(pivot=(472,418), poly=[(398,330),(420,282),(478,268),(525,290),(542,340),(528,400),(514,432),(430,434),(422,414),(398,405),(388,365)]),
 'pescoco': dict(pivot=(475,455), poly=[(424,388),(522,386),(518,432),(516,470),(426,470),(420,432)]),
 'peito':   dict(pivot=(478,560), poly=[(368,448),(430,428),(530,428),(578,448),(596,500),(584,560),(592,640),(595,692),(355,702),(348,620),(342,545),(350,485)]),
 'barriga': dict(pivot=(478,655), poly=[(355,618),(595,608),(596,720),(606,790),(600,840),(372,854),(362,780),(354,698)]),
 'bracoE':  dict(pivot=(360,495), poly=[(352,458),(392,478),(378,540),(352,586),(332,626),(342,662),(292,670),(264,620),(266,560),(272,520),(300,478)]),
 'anteE':   dict(pivot=(310,628), poly=[(290,598),(340,608),(354,640),(362,676),(348,702),(298,690),(270,656),(266,616)]),
 'bracoD':  dict(pivot=(578,488), poly=[(548,448),(600,452),(630,482),(638,524),(620,552),(640,590),(658,620),(628,642),(602,612),(592,560),(576,516),(556,486)]),
 'anteD':   dict(pivot=(644,602), poly=[(614,576),(658,578),(678,620),(680,690),(668,736),(612,740),(606,700),(608,650)]),
 'maoD':    dict(pivot=(638,728), poly=[(610,704),(670,704),(674,756),(652,796),(616,782),(608,740)]),
 'quadril': dict(pivot=(490,815), poly=[(368,790),(606,778),(632,830),(624,894),(540,902),(520,872),(492,896),(430,906),(372,896),(360,840)]),
 'coxaE':   dict(pivot=(440,845), poly=[(378,820),(500,812),(512,880),(498,950),(492,994),(420,998),(402,940),(388,880)]),
 'canelaE': dict(pivot=(462,975), poly=[(424,940),(498,938),(503,1010),(497,1070),(418,1070),(412,1020),(420,990)]),
 'peE':     dict(pivot=(462,1058), poly=[(412,1034),(502,1030),(499,1086),(501,1118),(407,1118),(409,1076)]),
 'coxaD':   dict(pivot=(548,838), poly=[(492,812),(620,806),(640,870),(630,940),(618,984),(528,986),(516,920),(500,862)]),
 'canelaD': dict(pivot=(578,972), poly=[(530,940),(616,938),(620,1000),(625,1070),(551,1070),(552,1040),(540,985)]),
 'peD':     dict(pivot=(590,1058), poly=[(550,1034),(626,1030),(631,1086),(633,1118),(553,1118),(551,1076)]),
}
ORDEM = ['bracoE','anteE','peE','canelaE','coxaE','peD','canelaD','coxaD',
         'quadril','barriga','peito','pescoco','cabeca','bracoD','anteD','maoD']

for velha in glob.glob('assets/pecas/*.png'):  # apaga pecas que nao existem mais
    if os.path.basename(velha)[:-4] not in PECAS: os.remove(velha)

dados = {}
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
    f.write('// gerado por tools_fatiar.py — 16 pecas, coords da foto 944x1118\n')
    f.write('window.PECAS_DADOS = ' + json.dumps(dados, indent=1) + ';\n')
    f.write('window.ORDEM_DESENHO = ' + json.dumps(ORDEM) + ';\n')

# preview: remonta na pose original p/ conferir buracos
prev = Image.new('RGBA', (W,H), (30,30,40,255))
for nome in ORDEM:
    d = dados[nome]
    prev.alpha_composite(Image.open(d['file']), (d['bbox'][0], d['bbox'][1]))
prev.convert('RGB').save('assets/preview-remontado.jpg', quality=88)
print('preview -> assets/preview-remontado.jpg')
