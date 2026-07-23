# Orange P4DA adaptive icon specification

## Approved artwork scale

- Use `ART_SCALE = 0.68` on the 1080×1080 adaptive-icon canvas.
- Apply exactly the same scale to light, AMOLED, Monet and monochrome artwork.
- Do not let Monet or monochrome use the unscaled source mask.
- Centre the final visible bounds independently after scaling.
- Allow no more than 2 px difference between opposite margins at 1080×1080.

This scale was approved against the emulator's circular launcher mask. It gives
the P4DA mark breathing room comparable to the neighbouring 4PDA launcher icon.

## Required previews

Before replacing Android resources, verify all variants under:

1. circle;
2. squircle;
3. rounded-square.

The generator enforcing this specification is
`design/app-icon/generate_orange_p4da_adaptive.py`.
