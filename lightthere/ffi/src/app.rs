//! What is LightCraft's own: the session, how methods reach it, and the photo renderer.

use lightcraft_mcp::{Backend, Headless};
use serde_json::{Value, json};

use crate::Rendered;

pub(crate) struct Inner {
    h: Headless,
}

/// A session with an empty in-memory library. The person runs the app on their own machine, so file
/// access is the local CLI's (`Headless` has the filesystem hooks), not a remote client's.
pub(crate) fn new() -> Inner {
    Inner { h: Headless::default() }
}

/// Every method of the headless control protocol (`engine.execute`, `engine.commands`, `ui.render`,
/// `app.export`, …).
pub(crate) fn call(inner: &mut Inner, method: &str, params: Value) -> Result<Value, String> {
    inner.h.call(method, params)
}

/// Nothing is queued for the front end yet (the playbook's phase 6).
pub(crate) fn ui_requests(_inner: &mut Inner) -> Value {
    json!([])
}

/// The long edge, in surface units, of the photo at scale 1: a picture is shown at the size it is
/// rendered, so the unit is only a reference.
const REFERENCE_EDGE: f32 = 2000.0;
/// The long edge a render is never larger than: the pipeline's cost grows with it.
const MAX_EDGE: f32 = 4096.0;

/// `photos`: the photos shown (the library's visible ones in grid order); the current one is the
/// active photo. `width` and `height` are the current photo's size in surface units.
pub(crate) fn surface(inner: &mut Inner) -> Value {
    let s = &mut inner.h.session;
    let visible = s.visible_cloned();
    let active = s.active();
    let current = active.and_then(|a| visible.iter().position(|id| *id == a));
    let (w, h) = match active.and_then(|id| s.render_now(id, 64, 64).ok()) {
        Some(img) => {
            let img = img.image;
            let long = img.width.max(img.height).max(1) as f32;
            (img.width as f32 / long * REFERENCE_EDGE, img.height as f32 / long * REFERENCE_EDGE)
        }
        None => (0.0, 0.0),
    };
    json!({"kind": "photos", "pages": visible.len(), "current": current, "width": w, "height": h})
}

/// Photo `page` of the visible ones, rendered with its edits at `scale` (1 is [`REFERENCE_EDGE`] on the
/// long edge), straight alpha.
pub(crate) fn render(inner: &mut Inner, page: u32, scale: f32, max_pixels: u64) -> Option<Rendered> {
    let s = &mut inner.h.session;
    let visible = s.visible_cloned();
    let id = *visible.get(usize::try_from(page).ok()?)?;
    let edge = (REFERENCE_EDGE * scale).round().clamp(16.0, MAX_EDGE) as usize;
    let img = s.render_now(id, edge, edge).ok()?.image;
    if (img.width as u64) * (img.height as u64) > max_pixels {
        return None;
    }
    let mut rgba = Vec::with_capacity(img.data.len() * 4);
    for px in &img.data {
        rgba.extend_from_slice(px);
    }
    Some(Rendered { width: u32::try_from(img.width).ok()?, height: u32::try_from(img.height).ok()?, pages: u32::try_from(visible.len()).unwrap_or(u32::MAX), rgba })
}
