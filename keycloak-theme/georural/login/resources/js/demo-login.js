// TEMPORÁRIO, só para a apresentação: a tela de login já vem com o usuário e a senha do gestor.
// Quem abrir o site entra como gestor sem digitar nada. Depois da apresentação: tire este arquivo
// do "scripts" do theme.properties, reinicie o Keycloak e troque a senha do gestor.
(function () {
  var USUARIO = 'gestor';
  var SENHA = 'georural123';

  function preencher() {
    var usuario = document.getElementById('username');
    var senha = document.getElementById('password');
    if (!usuario || !senha) return; // outras telas (trocar senha, erro...) não são tocadas
    if (!usuario.value) usuario.value = USUARIO;
    // Só completa a senha do gestor: se a pessoa digitar outro usuário, a senha fica com ela.
    if (usuario.value.trim().toLowerCase() === USUARIO && !senha.value) senha.value = SENHA;
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', preencher);
  else preencher();
})();
