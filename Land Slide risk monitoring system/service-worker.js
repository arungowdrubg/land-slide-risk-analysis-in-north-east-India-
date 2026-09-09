const CACHE_NAME = "bhuraksha-safety-shell-v1";
const APP_SHELL = [
  "./bhuraksha-frontend.html",
  "./manifest.webmanifest",
  "./assets/pwa/bhuraksha-icon.svg"
];

self.addEventListener("install", event=>{
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache=>cache.addAll(APP_SHELL))
      .then(()=>self.skipWaiting())
  );
});

self.addEventListener("activate", event=>{
  event.waitUntil(
    caches.keys()
      .then(keys=>Promise.all(keys
        .filter(key=>key.startsWith("bhuraksha-") && key !== CACHE_NAME)
        .map(key=>caches.delete(key))))
      .then(()=>self.clients.claim())
  );
});

async function networkFirst(request){
  try{
    const response = await fetch(request);
    if(response.ok){
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  }catch(error){
    const cached = (await caches.match(request, {ignoreSearch:true}))
      || (await caches.match("./bhuraksha-frontend.html"));
    return cached || new Response("Offline safety guidance is unavailable until the app has been opened online once.", {
      status:503,
      statusText:"Service Unavailable"
    });
  }
}

async function cacheFirst(request){
  const cached = await caches.match(request);
  if(cached) return cached;

  try{
    const response = await fetch(request);
    if(response.ok){
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  }catch(error){
    return new Response("Offline resource unavailable", {status:503, statusText:"Service Unavailable"});
  }
}

self.addEventListener("fetch", event=>{
  const request = event.request;
  if(request.method !== "GET") return;
  const url = new URL(request.url);

  // Do not cache weather, map, model or other cross-origin live-data responses.
  if(url.origin !== self.location.origin) return;

  if(request.mode === "navigate"){
    event.respondWith(networkFirst(request));
    return;
  }
  event.respondWith(cacheFirst(request));
});
