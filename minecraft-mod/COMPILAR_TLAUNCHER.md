# Como compilar pro TLauncher (Windows)

Você já tem Java 17 (vem com o Minecraft). Faça:

1. Baixe o source: https://github.com/NEYMAROFICIALCONFIA123/MINECRAFT/raw/arena/cdaf6e80-minecraft/dist/minecraft-mod-source.zip
   Descompacte em C:\Ponte\

2. Abra CMD ou PowerShell na pasta minecraft-mod:
   ```
   cd C:\Ponte\minecraft-mod
   gradlew build
   ```
   Ou se der erro de wrapper:
   ```
   gradle build
   ```

3. O JAR sai em: `build\libs\minecraft-unity-ponte-1.0.1.jar`
   Copie para: `%appdata%\.minecraft\versions\Unity\mods\`

4. Se já usa TLauncher com versão "Unity":
   - Feche o launcher
   - Copie também `fabric-api` se não tiver
   - Abra de novo e divirta-se

Se não quiser compilar, baixe o JAR já compilado em:
  GitHub Actions > Build Ponte JAR > Artifacts
  https://github.com/NEYMAROFICIALCONFIA123/MINECRAFT/actions
