// Teste headless do ragdoll 2.0: 16 pecas / 15 juntas, fisica a 120Hz,
// tonus muscular, limite das juntas e auto-colisao — igualzinho ao jogo.
// Uso: node tests/sim_test.js
global.window = {};
const Matter = require('../lib/matter.min.js');
require('../pecas.js');
const PECAS_DADOS = global.window.PECAS_DADOS;
const { Engine, Bodies, Body, Composite, Constraint, Detector } = Matter;

const escala = 0.45;
const MAE = { pescoco:'peito', cabeca:'pescoco', bracoE:'peito', anteE:'bracoE',
  bracoD:'peito', anteD:'bracoD', maoD:'anteD', barriga:'peito', quadril:'barriga',
  coxaE:'quadril', canelaE:'coxaE', peE:'canelaE', coxaD:'quadril', canelaD:'coxaD', peD:'canelaD' };
const FK = ['peito','pescoco','cabeca','bracoE','anteE','bracoD','anteD','maoD',
  'barriga','quadril','coxaE','canelaE','peE','coxaD','canelaD','peD'];
const LIMITE = { pescoco:38, cabeca:48, bracoE:178, anteE:155, bracoD:178, anteD:155,
  maoD:75, barriga:42, quadril:38, coxaE:145, canelaE:150, peE:65, coxaD:145, canelaD:150, peD:65 };
const EM_PE = { peito:0, barriga:0, pescoco:-2, cabeca:-2, bracoE:10, anteE:-8, bracoD:20, anteD:8,
  maoD:5, quadril:0, coxaE:6, canelaE:4, peE:2, coxaD:12, canelaD:10, peD:8 };
let tonus = 0.25, poseAlvoRel = {}, autoColisao = true;

const rad = g => g*Math.PI/180, graus = r => r*180/Math.PI;
const enrolar = d => ((d+180)%360+360)%360-180;
function rot(v, g){ const t=rad(g), c=Math.cos(t), s=Math.sin(t); return {x:v.x*c-v.y*s, y:v.x*s+v.y*c}; }
const centro = n => PECAS_DADOS[n].centro, pivot = n => PECAS_DADOS[n].pivot;

const CAT_MUNDO = 0x0001;
const catPeca = n => 0x0002 << FK.indexOf(n);
function sobrepoe(a,b){
  const bb = n => { const d=PECAS_DADOS[n], [cx,cy]=d.centro;
    return [cx-d.bbox[2]*0.41, cy-d.bbox[3]*0.44, cx+d.bbox[2]*0.41, cy+d.bbox[3]*0.44]; };
  const A=bb(a), B=bb(b);
  return Math.max(0,Math.min(A[2],B[2])-Math.max(A[0],B[0])) * Math.max(0,Math.min(A[3],B[3])-Math.max(A[1],B[1]));
}
function filtroPeca(nome){
  let m = CAT_MUNDO;
  if (autoColisao) for (const o of FK){
    if (o===nome || MAE[o]===nome || MAE[nome]===o || sobrepoe(nome,o)>0) continue;
    m |= catPeca(o);
  }
  return { category: catPeca(nome), mask: m };
}
const FILTRO_MUNDO = { category: CAT_MUNDO, mask: 0x1FFFF };

const L = 1280, A = 800, chaoY = A-64;
const engine = Engine.create({ positionIterations:10, velocityIterations:8, constraintIterations:6 });
Composite.add(engine.world, [
  Bodies.rectangle(L/2, chaoY+40, L+400, 80, { isStatic:true, friction:0.9, collisionFilter:FILTRO_MUNDO }),
  Bodies.rectangle(-35, A/2, 70, A*3, { isStatic:true, collisionFilter:FILTRO_MUNDO }),
  Bodies.rectangle(L+35, A/2, 70, A*3, { isStatic:true, collisionFilter:FILTRO_MUNDO }),
]);

function posicoes(ang, raiz){
  const pos = { peito:{...raiz} };
  for (const n of FK.slice(1)){
    const mae=MAE[n], j=pivot(n), cm=centro(mae), cp=centro(n);
    const jw = rot({x:(j[0]-cm[0])*escala, y:(j[1]-cm[1])*escala}, ang[mae]||0);
    const off = rot({x:(cp[0]-j[0])*escala, y:(cp[1]-j[1])*escala}, ang[n]||0);
    pos[n] = { x:pos[mae].x+jw.x+off.x, y:pos[mae].y+jw.y+off.y };
  }
  return pos;
}
function montar(x, y, ang){
  const pos = posicoes(ang, {x,y}), corpos = {};
  for (const n of FK){
    const d = PECAS_DADOS[n], pesada = (n==='peito'||n==='barriga');
    corpos[n] = Bodies.rectangle(pos[n].x, pos[n].y,
      Math.max(12, d.bbox[2]*escala*0.82), Math.max(12, d.bbox[3]*escala*0.88), {
      angle: rad(ang[n]||0), label:'peca:'+n, slop:0.02,
      chamfer:{ radius: Math.min(d.bbox[2], d.bbox[3])*escala*0.28 },
      friction: (n==='peE'||n==='peD') ? 0.95 : 0.55, frictionStatic:0.9,
      frictionAir: pesada ? 0.012 : 0.018, restitution:0.05,
      density: pesada ? 0.0016 : 0.001, collisionFilter: filtroPeca(n) });
  }
  const lig = [];
  for (const n of FK.slice(1)){
    const mae=MAE[n], j=pivot(n), cm=centro(mae), cp=centro(n);
    lig.push(Constraint.create({ bodyA:corpos[mae],
      pointA:{x:(j[0]-cm[0])*escala, y:(j[1]-cm[1])*escala},
      bodyB:corpos[n], pointB:{x:(j[0]-cp[0])*escala, y:(j[1]-cp[1])*escala},
      length:0, stiffness:0.95, damping:0.08 }));
  }
  Composite.add(engine.world, [...Object.values(corpos), ...lig]);
  poseAlvoRel = {};
  for (const n of FK.slice(1)) poseAlvoRel[n] = enrolar((ang[n]||0)-(ang[MAE[n]]||0));
  return { corpos, lig };
}

let craque;
function juntaMundo(mae, filho){
  const b=craque.corpos[mae], j=pivot(filho), c=centro(mae);
  const r = rot({x:(j[0]-c[0])*escala, y:(j[1]-c[1])*escala}, graus(b.angle));
  return { x:b.position.x+r.x, y:b.position.y+r.y };
}
function girarAoRedor(b, ponto, dRad){
  const r = rot({x:b.position.x-ponto.x, y:b.position.y-ponto.y}, graus(dRad));
  Body.setPosition(b, {x:ponto.x+r.x, y:ponto.y+r.y}); Body.setAngle(b, b.angle+dRad);
}
let maiorExcessoLimite = 0;
function aplicarTonusELimites(){
  for (const n of FK.slice(1)){
    const bP=craque.corpos[MAE[n]], b=craque.corpos[n];
    const rel = enrolar(graus(b.angle-bP.angle));
    if (tonus>0){
      const erro = rad(enrolar((poseAlvoRel[n]||0)-rel));
      Body.setAngularVelocity(b, b.angularVelocity + erro*tonus*0.055);
      Body.setAngularVelocity(bP, bP.angularVelocity - erro*tonus*0.012);
    }
    const lim = LIMITE[n] ?? 170;
    if (Math.abs(rel) > lim){
      const excesso = rad(rel - Math.sign(rel)*lim);
      girarAoRedor(b, juntaMundo(MAE[n], n), -excesso*0.7);
      const giroRel = b.angularVelocity - bP.angularVelocity;
      Body.setAngularVelocity(b, bP.angularVelocity + giroRel*0.15 - excesso*0.6);
    } else if (Math.abs(rel) > lim*0.82){
      const giroRel = b.angularVelocity - bP.angularVelocity;
      if (Math.sign(giroRel) === Math.sign(rel))
        Body.setAngularVelocity(b, b.angularVelocity - giroRel*0.25);
    }
    if (Math.abs(b.angularVelocity) > 0.85)
      Body.setAngularVelocity(b, Math.sign(b.angularVelocity)*0.85);
  }
}
const PASSO = 1000/120;
function passo(segundos){
  const n = Math.round(segundos*120);
  for (let i=0;i<n;i++){ Engine.update(engine, PASSO); aplicarTonusELimites();
    for (const nome of FK.slice(1)){ const rel = Math.abs(enrolar(graus(
      craque.corpos[nome].angle - craque.corpos[MAE[nome]].angle)));
      maiorExcessoLimite = Math.max(maiorExcessoLimite, rel - LIMITE[nome]); } }
}
function conferir(rotulo){
  let maxEst = 0, energia = 0;
  for (const n of FK){
    const b = craque.corpos[n];
    if (!isFinite(b.position.x+b.position.y+b.angle)) throw new Error(`${rotulo}: ${n} virou NaN!`);
    if (b.position.y > A+400) throw new Error(`${rotulo}: ${n} caiu do mundo`);
    energia = Math.max(energia, b.speed);
  }
  for (const n of FK.slice(1)){
    const a=juntaMundo(MAE[n],n), b=juntaMundo(n,n);
    maxEst = Math.max(maxEst, Math.hypot(a.x-b.x,a.y-b.y));
  }
  console.log(`${rotulo}: OK (junta +esticada ${maxEst.toFixed(1)}px, vel. máx ${energia.toFixed(1)})`);
  if (maxEst > 60) throw new Error(`${rotulo}: junta esticou demais (${maxEst.toFixed(0)}px)`);
  return energia;
}

/* ---------- os testes ---------- */
craque = montar(L/2, 290, EM_PE);
if (Object.keys(craque.corpos).length !== 16 || craque.lig.length !== 15)
  throw new Error('era pra ter 16 pecas e 15 juntas!');
console.log('montado: 16 pecas, 15 juntas ✓');

// auto-colisao: quem deve e quem nao deve bater
const pode = (a,b) => Detector.canCollide(craque.corpos[a].collisionFilter, craque.corpos[b].collisionFilter);
const bola = Bodies.circle(0,0,10,{ collisionFilter:FILTRO_MUNDO });
const testesFiltro = [
  ['peito','barriga', false, 'mae/filha nao colidem'],
  ['pescoco','cabeca', false, 'mae/filha nao colidem'],
  ['bracoE','barriga', false, 'vizinhas coladas na foto nao colidem'],
  ['coxaE','canelaD', true,  'perna bate na outra perna'],
  ['anteD','coxaD', true,  'braco bate na perna'],
  ['cabeca','peE', true,  'cabeca pode bater no pe (dancando)'],
];
for (const [a,b,esperado,desc] of testesFiltro){
  if (pode(a,b) !== esperado) throw new Error(`filtro ${a}x${b}: esperado ${esperado} (${desc})`);
  console.log(`filtro ${a} x ${b} = ${esperado} ✓ (${desc})`);
}
if (!Detector.canCollide(bola.collisionFilter, craque.corpos.peD.collisionFilter))
  throw new Error('bola nao colide com o pe direito!');
console.log('filtro bola x peD = true ✓ (chute na bola funciona)');

conferir('montado');
passo(4); conferir('caiu e desabou (4s com tonus 25%)');
if (craque.corpos.peito.position.y < 320) throw new Error('peito nao caiu?!');
console.log('peito descansando em y=' + craque.corpos.peito.position.y.toFixed(0), '(chao em', chaoY + ')');
const velParado = conferir('parado no chao');
if (velParado > 6) throw new Error('ficou tremendo no chao (vel ' + velParado.toFixed(1) + ')');

for (const n of FK){ Body.setVelocity(craque.corpos[n], {x:7,y:-20}); Body.setAngularVelocity(craque.corpos[n], 0.4); }
passo(5); conferir('super pulo girando (5s)');

for (const n of FK){ const b=craque.corpos[n];
  const dx=b.position.x-L/2, dy=b.position.y-chaoY, d=Math.hypot(dx,dy)||1;
  Body.setVelocity(b,{x:b.velocity.x+dx/d*16, y:b.velocity.y+dy/d*16-4});
  Body.setAngularVelocity(b, 0.6); }
passo(6); conferir('pos-explosao (6s)');

const mao = Constraint.create({ pointA:{x:200,y:150}, bodyB:craque.corpos.maoD,
  pointB:{x:0,y:15}, stiffness:0.18, damping:0.08, length:0 });
Composite.add(engine.world, mao);
for (let i=0;i<480;i++){ // balança ele pela mão a 120Hz igual ao jogo
  mao.pointA = { x: L/2 + Math.sin(i/36)*420, y: 160 + Math.cos(i/46)*60 };
  Engine.update(engine, PASSO); aplicarTonusELimites();
}
Composite.remove(engine.world, mao);
passo(3); conferir('arrastado pela mao e solto (7s)');

// tonus no talo: ele tenta segurar a pose mesmo apanhando
tonus = 1;
for (const n of FK) Body.setAngularVelocity(craque.corpos[n], 0.5);
passo(4); conferir('tonus 100% apanhando (4s)');
tonus = 0.25;

console.log(`maior excesso dos limites das juntas durante tudo: ${maiorExcessoLimite.toFixed(1)}° (corrigido em voo)`);
if (maiorExcessoLimite > 45) throw new Error('limite de junta estourou demais');
console.log('\n✅ TODOS OS TESTES PASSARAM — física 2.0 firme, com limite e auto-colisão 😅💪');
