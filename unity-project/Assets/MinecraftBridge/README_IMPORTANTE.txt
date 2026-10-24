COMO ABRIR CORRETAMENTE:

1) NÃO clique em "Open" no Unity Hub com o arquivo unity-package.zip
   -> unity-package NÃO é um projeto, é um pacote!

2) Faça assim:
   - Unity Hub > New Project > 3D (URP) > Crie um projeto vazio
   - Com o projeto aberto: Window > Package Manager > + > Add package from disk
   - Selecione: .../unity-package/package.json

OU (mais fácil, já pronto):
   - Unity Hub > Open > Selecione a pasta "unity-project" (essa pasta aqui)
   - Ela JÁ é um projeto válido Unity 2021.3

ERRO "Please select a valid project" = você tentou abrir a pasta do pacote como projeto.
