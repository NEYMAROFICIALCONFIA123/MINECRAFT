// Teste headless do ragdoll: monta igual ao jogo, derruba no chão,
// joga pra cima, explode... e confere que nada vira NaN nem desmonta.
// Uso: node tests/sim_test.js
global.window = {};
const Matter = require('../lib/matter.min.js');
require('../pecas.js'); // popula window.PECAS_DADOS / ORDEM_DESENHO
const PECAS_DADOS = global.window.PECAS_DADOS;
const { Engine, Bodies, Body, Composite, Constraint } = Matter;

const escala = 0.45;
const MAE = { cabeca:'tronco', bracoE:'tronco', bracoD:'tronco', anteD:'bracoD',
              quadril:'tronco', coxaE:'quadril', canelaE:'coxaE', coxaD:'quadril', canelaD:'coxaD' };
const FK_ORDEM = ['tronco','cabeca','bracoE','bracoD','anteD','quadril','coxaE','canelaE','coxaD','canelaD'];
const EM_PE = { tronco:0, cabeca:-3, bracoE:10, bracoD:20, anteD:8, quadril:0, coxaE:6, canelaE:4, coxaD:12, canelaD:10 };
const rad = g => g * Math.PI / 180;
function rot(v, graus){ const t = rad(graus), c = Math.cos(t), s = Math.sin(t);
  return { x: v.x*c - v.y*s, y: v.x*s + v.y*c }; }
const centro = n => PECAS_DADOS[n].centro, pivot = n => PECAS_DADOS[n].pivot;

const L = 1280, A = 800, chaoY = A - 64;
const engine = Engine.create();
Composite.add(engine.world, [
  Bodies.rectangle(L/2, chaoY+40, L+400, 80, { isStatic:true }),
  Bodies.rectangle(-35, A/2, 70, A*3, { isStatic:true }),
  Bodies.rectangle(L+35, A/2, 70, A*3, { isStatic:true }),
]);

// --- mesma montagem do index.html ---
function montar(x, y, angulos){
  const pos = { tronco:{x,y} };
  for (const nome of FK_ORDEM.slice(1)){
    const mae = MAE[nome], junta = pivot(nome), cm = centro(mae), cp = centro(nome);
    const jw = rot({x:(junta[0]-cm[0])*escala, y:(junta[1]-cm[1])*escala}, angulos[mae]);
    const off = rot({x:(cp[0]-junta[0])*escala, y:(cp[1]-junta[1])*escala}, angulos[nome]);
    pos[nome] = { x: pos[mae].x+jw.x+off.x, y: pos[mae].y+jw.y+off.y };
  }
  const corpos = {};
  for (const nome of FK_ORDEM){
    const d = PECAS_DADOS[nome];
    corpos[nome] = Bodies.rectangle(pos[nome].x, pos[nome].y,
      Math.max(14, d.bbox[2]*escala*0.82), Math.max(14, d.bbox[3]*escala*0.88), {
      angle: rad(angulos[nome]), label:'peca:'+nome,
      chamfer:{ radius: Math.min(d.bbox[2], d.bbox[3])*escala*0.28 },
      friction:0.6, frictionAir:0.016, restitution:0.05,
      density: nome==='tronco' ? 0.0016 : 0.001,
      collisionFilter:{ group:-1 },
    });
  }
  const lig = [];
  for (const nome of FK_ORDEM.slice(1)){
    const mae = MAE[nome], junta = pivot(nome), cm = centro(mae), cp = centro(nome);
    lig.push(Constraint.create({
      bodyA: corpos[mae], pointA:{x:(junta[0]-cm[0])*escala, y:(junta[1]-cm[1])*escala},
      bodyB: corpos[nome], pointB:{x:(junta[0]-cp[0])*escala, y:(junta[1]-cp[1])*escala},
      length:0, stiffness:0.95, damping:0.08 }));
  }
  Composite.add(engine.world, [...Object.values(corpos), ...lig]);
  return { corpos, lig };
}

function juntaMundo(corpos, maeNome, filho){
  const b = corpos[maeNome], p = pivot(filho), c = centro(maeNome);
  const r = rot({x:(p[0]-c[0])*escala, y:(p[1]-c[1])*escala}, b.angle*180/Math.PI);
  return { x:b.position.x+r.x, y:b.position.y+r.y };
}
function conferir(corpos, rotulo){
  let maxEsticada = 0;
  for (const n of FK_ORDEM){
    const b = corpos[n];
    if (!isFinite(b.position.x) || !isFinite(b.position.y) || !isFinite(b.angle))
      throw new Error(`${rotulo}: ${n} virou NaN!`);
    if (b.position.y > A + 400) throw new Error(`${rotulo}: ${n} caiu do mundo (y=${b.position.y})`);
  }
  for (const nome of FK_ORDEM.slice(1)){
    const a = juntaMundo(corpos, MAE[nome], nome), b = juntaMundo(corpos, nome, nome);
    maxEsticada = Math.max(maxEsticada, Math.hypot(a.x-b.x, a.y-b.y));
  }
  console.log(`${rotulo}: OK (maior esticada de junta = ${maxEsticada.toFixed(1)}px)`);
  if (maxEsticada > 60) throw new Error(`${rotulo}: junta esticou demais (${maxEsticada}px) — ragdoll desmontando!`);
}
const passo = n => { for (let i=0;i<n;i++) Engine.update(engine, 16.666); };

// 1) cai em pé e desaba no chão
const { corpos } = montar(L/2, 290, EM_PE);
conferir(corpos, 'montado');
passo(240); conferir(corpos, 'caiu e desabou (4s)');
const yTronco = corpos.tronco.position.y;
if (yTronco < 300) throw new Error('tronco não caiu?! y=' + yTronco);
console.log('tronco descansando em y=' + yTronco.toFixed(0), '(chão em', chaoY + ')');

// 2) super pulo + giro maluco
for (const n of FK_ORDEM){ Body.setVelocity(corpos[n], {x:6, y:-19}); Body.setAngularVelocity(corpos[n], 0.3); }
passo(300); conferir(corpos, 'super pulo (5s)');

// 3) explosão embaixo dele
for (const n of FK_ORDEM){ const b = corpos[n];
  const dx=b.position.x-L/2, dy=b.position.y-chaoY, d=Math.hypot(dx,dy)||1;
  Body.setVelocity(b,{ x:b.velocity.x+dx/d*15, y:b.velocity.y+dy/d*15-4 }); }
passo(360); conferir(corpos, 'pós-explosão (6s)');

// 4) arrastar pela mão (constraint igual à ferramenta ✋) e balançar
const mao = Constraint.create({ pointA:{x:200,y:150}, bodyB:corpos.anteD,
  pointB:{x:0,y:30}, stiffness:0.14, damping:0.08, length:0 });
Composite.add(engine.world, mao);
for (let i=0;i<240;i++){ mao.pointA = { x: L/2 + Math.sin(i/18)*420, y: 160 + Math.cos(i/23)*60 };
  Engine.update(engine, 16.666); }
Composite.remove(engine.world, mao);
passo(180); conferir(corpos, 'arrastado pela mão e solto (7s)');

console.log('\n✅ TODOS OS TESTES PASSARAM — o craque sobrevive (mais ou menos) 😅');
