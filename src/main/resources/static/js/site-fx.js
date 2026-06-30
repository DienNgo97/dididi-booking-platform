/* ============================================================
   Dididi — site-fx.js : lớp hiệu ứng dùng chung cho MỌI trang.
   - Nạp web font (graceful, offline tự fallback hệ thống).
   - Scroll-reveal tự động (chỉ phần dưới màn hình -> không nháy nội dung trên).
   - Count-up cho [data-countup].
   - Parallax nhẹ cho .hero.
   Phòng thủ: mọi lỗi đều nuốt, không làm hỏng trang. Tôn trọng reduced-motion.
   ============================================================ */
(function () {
  "use strict";

  // 1) Web font (Plus Jakarta Sans). Nếu offline -> fallback hệ thống trong CSS.
  try {
    var pc = document.createElement("link");
    pc.rel = "preconnect"; pc.href = "https://fonts.gstatic.com"; pc.crossOrigin = "anonymous";
    document.head.appendChild(pc);
    var f = document.createElement("link");
    f.rel = "stylesheet";
    f.href = "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap";
    document.head.appendChild(f);
  } catch (e) { /* ignore */ }

  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion:reduce)").matches;

  function ready(fn) {
    if (document.readyState !== "loading") fn();
    else document.addEventListener("DOMContentLoaded", fn);
  }

  ready(function () {
    if (reduce) return;

    // 2) Scroll-reveal: chỉ gắn cho phần tử NẰM DƯỚI màn hình đầu tiên
    //    (phần tử trên màn hình giữ nguyên -> tránh nháy/FOUC).
    if ("IntersectionObserver" in window) {
      try {
        var sel = ".card,.list-item,.hcard,.fcard,.th-card,.feat,.stat," +
                  ".support-faq button,.wrap>h2,.tbl,.gallery,.pax-card,.dt-head,.loyalty-card,.pay-side";
        var els = Array.prototype.slice.call(document.querySelectorAll(sel));
        var vh = window.innerHeight || 800;
        var io = new IntersectionObserver(function (ents) {
          ents.forEach(function (en) {
            if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); }
          });
        }, { threshold: 0.08, rootMargin: "0px 0px -5% 0px" });
        var stagger = 0;
        els.forEach(function (el) {
          var top = el.getBoundingClientRect().top;
          if (top > vh * 0.86) {                 // dưới màn hình đầu -> reveal khi cuộn tới
            el.classList.add("reveal");
            el.style.transitionDelay = ((stagger % 4) * 60) + "ms";
            stagger++;
            io.observe(el);
          }
        });
      } catch (e) { /* ignore */ }
    }

    // 3) Count-up cho [data-countup="500"] [data-suffix="+"]
    try {
      var nums = Array.prototype.slice.call(document.querySelectorAll("[data-countup]"));
      var run = function (el) {
        var end = parseFloat(el.getAttribute("data-countup")) || 0;
        var suf = el.getAttribute("data-suffix") || "";
        var dec = (end % 1 !== 0) ? 1 : 0;
        var dur = 1300, t0 = null;
        function step(ts) {
          if (!t0) t0 = ts;
          var p = Math.min((ts - t0) / dur, 1);
          var e = 1 - Math.pow(1 - p, 3);           // easeOutCubic
          var v = end * e;
          el.textContent = (dec ? v.toFixed(1) : Math.round(v).toLocaleString("vi-VN")) + suf;
          if (p < 1) requestAnimationFrame(step);
        }
        requestAnimationFrame(step);
      };
      if (nums.length) {
        if ("IntersectionObserver" in window) {
          var io2 = new IntersectionObserver(function (es) {
            es.forEach(function (en) { if (en.isIntersecting) { run(en.target); io2.unobserve(en.target); } });
          }, { threshold: 0.4 });
          nums.forEach(function (n) { io2.observe(n); });
        } else {
          nums.forEach(run);
        }
      }
    } catch (e) { /* ignore */ }

    // 4) Parallax nhẹ cho .hero (đặt --py, ::before kế thừa biến này)
    try {
      var hero = document.querySelector(".hero");
      if (hero) {
        var ticking = false;
        var onScroll = function () {
          if (ticking) return; ticking = true;
          requestAnimationFrame(function () {
            var y = window.pageYOffset || document.documentElement.scrollTop || 0;
            if (y < 760) hero.style.setProperty("--py", (y * 0.16) + "px");
            ticking = false;
          });
        };
        window.addEventListener("scroll", onScroll, { passive: true });
        onScroll();
      }
    } catch (e) { /* ignore */ }

    // 5) Hero LAI (.hero-kb): slideshow Ken Burns + chữ điểm đến đổi theo ảnh + dots + thanh tiến trình
    try {
      var cine = document.querySelector(".hero-cine");
      if (cine) {
        var slides = Array.prototype.slice.call(cine.querySelectorAll(".hero-slide"));
        var rotEl = document.getElementById("heroRot");
        var dotsBox = document.getElementById("heroDots");
        var heroBar = document.getElementById("heroBar");
        var cities = ["Phú Quốc", "Vịnh Hạ Long", "Sa Pa", "Nha Trang"];
        if (slides.length) {
          var dots = [];
          var setWord = function (c) {
            if (!rotEl) return;
            rotEl.style.animation = "none"; void rotEl.offsetWidth;
            rotEl.textContent = c; rotEl.style.animation = "fxWordIn .6s var(--ease)";
          };
          var runBar = function () {
            if (!heroBar) return;
            heroBar.classList.remove("run"); void heroBar.offsetWidth; heroBar.classList.add("run");
          };
          var show = function (i) {
            slides.forEach(function (s, k) { s.classList.toggle("on", k === i); if (dots[k]) dots[k].classList.toggle("on", k === i); });
            setWord(cities[i % cities.length]); runBar(); idx = i;
          };
          var next = function () { show((idx + 1) % slides.length); };
          var go = function (i) { show(i); clearInterval(timer); timer = setInterval(next, 6000); };
          var idx = 0, timer;
          if (dotsBox) {
            slides.forEach(function (_, k) {
              var b = document.createElement("b");
              if (k === 0) b.className = "on";
              b.addEventListener("click", function () { go(k); });
              dotsBox.appendChild(b); dots.push(b);
            });
          }
          setWord(cities[0]); runBar();
          timer = setInterval(next, 6000);
        }
      }
    } catch (e) { /* ignore */ }
  });
})();
