//! LightCraft's own: a session starts with an empty library, a photo imports and renders.

use super::*;

fn exec(s: *mut LokiSession, command: &str, params: serde_json::Value) -> serde_json::Value {
    let body = serde_json::json!({"command": command, "params": params}).to_string();
    let r = call(s, "engine.execute", Some(&body));
    assert_eq!(r["ok"], true, "{command}: {r}");
    r["result"].clone()
}

#[test]
fn a_new_session_has_no_photos_and_renders_nothing() {
    let s = loki_session_new();
    let info = call(s, "loki.surface", None)["result"].clone();
    assert_eq!(info["pages"], 0, "{info}");
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    assert!(unsafe { loki_render_page(s, 0, 0.1, &mut w, &mut h, &mut n) }.is_null());
    unsafe { loki_session_free(s) };
}

#[test]
fn an_imported_photo_shows_and_renders() {
    let dir = std::env::temp_dir().join(format!("loki-ffi-light-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("p.png");
    let mut png = Vec::new();
    {
        // An 8 x 4 grey PNG, written by hand-free means: a tiny valid file from the engine's own encoder.
        let img = lightcraft_raster::Rgba8::from_fn(8, 4, |x, y| [(x * 30) as u8, (y * 60) as u8, 90, 255]);
        png = lightcraft_mcp::encode_image("png", &img, 90).unwrap_or(png);
    }
    std::fs::write(&path, &png).unwrap();
    let s = loki_session_new();
    exec(s, "library.import", serde_json::json!({"paths": [path.to_string_lossy()]}));
    let info = call(s, "loki.surface", None)["result"].clone();
    assert_eq!(info["pages"], 1, "{info}");
    assert!(info["width"].as_f64().unwrap_or(0.0) > info["height"].as_f64().unwrap_or(1.0), "{info}");
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    let px = unsafe { loki_render_page(s, 0, 0.1, &mut w, &mut h, &mut n) };
    assert!(!px.is_null() && w > 0 && h > 0 && n == 1, "{info}");
    unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    // Past the last photo: no picture, no crash.
    assert!(unsafe { loki_render_page(s, 5, 1.0, &mut w, &mut h, &mut n) }.is_null());
    unsafe { loki_session_free(s) };
    let _ = std::fs::remove_dir_all(dir);
}
