"""Recorta o jogador da foto e detecta as articulacoes (MediaPipe).
Uso (dentro de ragdoll-sandbox/): python3 tools_recortar.py
Requer: mediapipe==0.10.14, opencv-python-headless, Pillow (pip install)
Gera: assets/jogador-recortado.png + assets/juntas.json
"""
import cv2, json
import numpy as np
import mediapipe as mp
from PIL import Image

SRC = 'assets/foto-original.png'
img_bgr = cv2.imread(SRC)
img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
h, w = img_rgb.shape[:2]
print('img', w, h)

# --- segmentacao da pessoa ---
seg = mp.solutions.selfie_segmentation.SelfieSegmentation(model_selection=1)
mask = seg.process(img_rgb).segmentation_mask  # float 0..1
alpha = (np.clip((mask - 0.35) / 0.3, 0, 1) * 255).astype(np.uint8)
# limpa ruido: mantem maior componente
num, labels, stats, _ = cv2.connectedComponentsWithStats((alpha > 127).astype(np.uint8), 8)
if num > 1:
    biggest = 1 + np.argmax(stats[1:, cv2.CC_STAT_AREA])
    alpha = np.where(labels == biggest, alpha, 0).astype(np.uint8)
alpha = cv2.morphologyEx(alpha, cv2.MORPH_CLOSE, np.ones((5,5), np.uint8))
alpha = cv2.GaussianBlur(alpha, (3,3), 0)

rgba = np.dstack([img_rgb, alpha])
Image.fromarray(rgba).save('assets/jogador-recortado.png')
ys, xs = np.where(alpha > 127)
print('bbox pessoa:', int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()))

# --- pose (articulacoes) ---
pose = mp.solutions.pose.Pose(static_image_mode=True, model_complexity=1)
res = pose.process(img_rgb)
names = ['nariz','olhoE_in','olhoE','olhoE_out','olhoD_in','olhoD','olhoD_out','orelhaE','orelhaD',
 'bocaE','bocaD','OMBRO_E','OMBRO_D','COTOVELO_E','COTOVELO_D','PULSO_E','PULSO_D',
 'mindinhoE','mindinhoD','dedoE','dedoD','polegarE','polegarD','QUADRIL_E','QUADRIL_D',
 'JOELHO_E','JOELHO_D','TORNOZELO_E','TORNOZELO_D','calcanharE','calcanharD','peE','peD']
juntas = {}
if res.pose_landmarks:
    for i, lm in enumerate(res.pose_landmarks.landmark):
        juntas[names[i]] = [round(lm.x*w,1), round(lm.y*h,1), round(lm.visibility,2)]
        print(names[i], juntas[names[i]])
json.dump({'w':w,'h':h,'bbox':[int(xs.min()),int(ys.min()),int(xs.max()),int(ys.max())],'juntas':juntas},
          open('assets/juntas.json','w'), indent=1, ensure_ascii=False)
print('ok -> assets/jogador-recortado.png, assets/juntas.json')
