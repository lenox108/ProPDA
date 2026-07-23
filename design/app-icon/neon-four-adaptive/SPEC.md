# Neon wireframe "4" launcher icon

Second launcher icon, alongside the orange P4DA mark. Built by
`design/app-icon/generate_neon_four_adaptive.py` from `neon-four-master.png`.

## Adaptive scale

Inherited from `../orange-p4da-adaptive/SPEC.md`:

- the digit measures `MARK_SCALE = 0.68` of the 1080×1080 canvas on its long side;
- the same placement is used for light, AMOLED, Monet and monochrome — every
  variant is cropped, scaled and centred from one measurement of the master;
- the final mask is centred independently, and `assert_centred` fails the build
  if opposite margins differ by more than 2 px;
- previews are rendered under circle, squircle and rounded-square masks.

Size and centring are measured at 30% of each rendering's peak alpha, not at a
fixed opacity: the neon layer keeps a glow past the lattice while the monochrome
mask stops dead at it, and an absolute floor would compare those falloffs rather
than the mark.

## Decisions taken during the build

- **Backgrounds are the app's, not the master's.** `#FFFFFF` in light,
  `#000000` in dark (`amoled_background_base`). The dark-navy circuit panel that
  came with the artwork is unused: an adaptive background must bleed to all
  108dp, and that panel carries its own rounded corners and drop shadow.
- **Light theme is ink, not neon.** Cyan light on white is invisible, so the
  same lattice is drawn in deep teal `#0B4A63`, with the struts weighted apart
  from the body — one flat alpha collapses the digit into a blob.
- **Monochrome is the solid silhouette.** The literal wireframe disintegrates
  below 96 px, and the engraved variant thins out badly under a dark Monet tint,
  where the knocked-out lattice eats the light glyph. Both are still generated
  for comparison (`--apply wireframe|engraved`).
- **The counter of the 4 stays open.** Holes are separated by size: the lattice
  interstices are filled, the counter is cut back out.
- **The master's watermark is dropped** by keeping only the connected component
  the digit belongs to. Left in, it joins the visible bounds and every later
  measurement is taken against the digit plus the watermark.

## Required previews

`neon-four-review.png`, `neon-four-monochrome-options.png`,
`neon-four-light-options.png`, `neon-four-sizes.png`,
`neon-four-monochrome-dark-tint.png`.

Resources are only written with `--apply`, after a board has been approved.
