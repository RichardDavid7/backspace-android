# Checklist Mestre de Paridade — Backspace Android (Web + Mumble/Mumla)

> **Regra de versionamento (SemVer a partir de `0.10.0`):**
> - **Patch (`0.10.1`, `0.10.2`...):** correções de bugs, ajustes visuais ou melhorias pontuais.
> - **Minor (`0.11.0`, `0.12.0`...):** novos blocos relevantes de recursos ou novos módulos.
> - **Major (`1.0.0`):** paridade completa atingida e estável.

---

## Bloco A — Navegação, Não Lidas, Menções & Figurinhas (`v0.10.0`)
- [x] Página principal = **Mensagens** (`Mensagens | Espaços | Configurações`)
- [x] Bolinha/badge de **mensagens não lidas** nas DMs e no ícone **Mensagens** da barra inferior
- [x] Bolinha/badge de **menções** no ícone **Espaços**, no seletor de cada Espaço e no canal com menção
- [x] Envio e renderização correta de **Figurinhas** (`sticker:<url>` WebP) no Chat e DMs

---

## Bloco B — Recursos de Áudio & Eficiência estilo Mumble/Mumla (`v0.11.0`)
- [x] **Modos de Transmissão:** Atividade de Voz (VAD) vs **Push-to-Talk (PTT)** (botão grande na sala de voz + botão físico de Volume+/Volume-)
- [x] **Medidor de nível de microfone ao vivo (VU Meter)** + **Slider de sensibilidade/threshold VAD** nas Configurações de Áudio
- [x] **Volume mestre de saída (0–200%)** integrado ao volume individual por pessoa
- [x] **Atenuação (Audio Ducking):** reduzir volume da sala automaticamente enquanto você fala
- [x] **Opus avançado estilo Mumble:** tamanho de pacote/frame (`10ms / 20ms / 40ms`), modo economia de dados (4G/5G · 24 kbps) e qualidade de conexão ao vivo
- [x] **Sons nativos do Backspace** (`mute`, `unmute`, `deafen`, `undeafen`, `user_join`, `user_leave`, `disconnect`, `message`) via `SoundPool` com toggle nas Configurações
- [x] **Persistência das preferências de áudio/vídeo** em `SharedPreferences` (`AudioPreferencesStorage`)

---

## Bloco C — Chat Avançado (Paridade Web) (`v0.12.0`)
- [x] **Markdown completo** (negrito, itálico, código inline, blocos de código, links clicáveis, spoilers)
- [x] **Autocomplete de `@menções`** ao digitar `@` (membros, cargos, `@everyone`, `@here`) + destaque visual na mensagem
- [x] **Renderização de Embeds ricos** (título, descrição, imagem, vídeo/GIF inline) e **Cartões de Convite de Espaço** clicáveis
- [x] **Upload e gerenciamento de Figurinhas pessoais** (`POST /api/stickers`, excluir)
- [x] **Upload de Avatar** do próprio usuário nas Configurações de Perfil

---

## Bloco D — Voz, Chamadas & Soundboard Completo (`v0.13.0`)
- [ ] **Tela de Chamada Recebida (Incoming Call)** com toque e botões Atender / Recusar
- [ ] **Picture-in-Picture (PiP)** nativo do Android ao minimizar durante transmissão de tela/vídeo
- [ ] **Qualidade de Screen Share configurável** (720p/1080p, 15/30/60 FPS, bitrate) respeitando limites da instância
- [ ] **Soundboard Manager** (upload de novos clipes, renomear, excluir, organizar pastas, configurações por Espaço)
- [ ] **Chat integrado dentro da Call** (painel lateral/modal sem sair da tela de voz)

---

## Bloco E — Organização, Notificações & Administração Avançada (`v0.14.0`)
- [ ] **Pastas de Espaços** (`SpaceFolder`) e reordenação
- [ ] **Configurações de Notificação por Espaço e por Canal** (Tudo / Apenas Menções / Silenciado)
- [ ] **Notificações Android em background** para DMs e Menções (com abertura direta na conversa)
- [ ] **Editor granular de Permissões por Cargo e Overrides por Canal/Categoria**
- [ ] **Gerenciamento de Membros no Admin** (atribuir/remover cargos, alterar apelido no Espaço, transferir posse)
- [ ] **Painel Admin da Instância: Usuários, Convites da Instância e Atualizações**
