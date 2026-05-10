(function () {
  // TODO: 替换为你的 Authing 配置
  const PORTAL_APP_ID = 'YOUR_AUTHING_APP_ID';
  const PORTAL_APP_HOST = 'https://YOUR_AUTH_HOST';
  const SCRIPT_URL = 'https://YOUR_PORTAL_HOST/static/lib/index.min.js';

  const statusEl = document.getElementById('status');
  const submitEl = document.getElementById('submit');
  const usernameEl = document.getElementById('username');
  const passwordEl = document.getElementById('password');

  function setStatus(message) {
    statusEl.textContent = message || '';
  }

  function reportError(message) {
//    setStatus(message);
    if (window.AndroidAuthBridge && typeof window.AndroidAuthBridge.onError === 'function') {
      window.AndroidAuthBridge.onError(message);
    }
  }

  function deliverToken(token) {
    if (window.AndroidAuthBridge && typeof window.AndroidAuthBridge.onToken === 'function') {
      window.AndroidAuthBridge.onToken(token);
      return;
    }
    reportError('原生桥接不可用');
  }

  function loadScript(src) {
    return new Promise((resolve, reject) => {
      if ([...document.scripts].some((script) => script.src === src)) {
        resolve();
        return;
      }
      const el = document.createElement('script');
      el.src = src;
      el.onload = resolve;
      el.onerror = () => reject(new Error(`加载脚本失败: ${src}`));
      document.head.appendChild(el);
    });
  }

  async function createClient() {
    if (!window.Authing) {
      await loadScript(SCRIPT_URL);
    }
    return new window.Authing.AuthenticationClient({
      appId: PORTAL_APP_ID,
      appHost: PORTAL_APP_HOST,
    });
  }

  async function login() {
    const username = usernameEl.value.trim();
    const password = passwordEl.value;
    if (!username || !password) {
      reportError('请输入账号和密码');
      return;
    }

    submitEl.disabled = true;
    setStatus('正在登录...');
    try {
      const client = await createClient();
      const result = await client.loginByAccount(username, password);
      const token = result?.token || result?.idToken || result?.id_token || result?.accessToken || result?.access_token;
      if (!token) {
        throw new Error('Authing 登录成功，但没有拿到 token');
      }
      setStatus('登录成功，正在返回 App...');
      deliverToken(token);
    } catch (error) {
      reportError(error?.message || '登录失败');
    } finally {
      submitEl.disabled = false;
    }
  }

  // 暴露给 Android 调用以预填账号密码
  window.prefill = function(user, pass) {
    if (user) usernameEl.value = user;
    if (pass) passwordEl.value = pass;
    if (user && pass) {
        // 如果都有，可以考虑延迟 500ms 自动点击登录
        setTimeout(() => {
            if (!submitEl.disabled) login();
        }, 500);
    }
  };

  submitEl.addEventListener('click', login);
})();
