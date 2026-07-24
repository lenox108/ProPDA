# Orange P4DA adaptive icon specification

## Approved artwork scale

- Use `ART_SCALE = 0.50` on the 1080×1080 adaptive-icon canvas.
- Apply 50% to the coloured light and AMOLED foregrounds.
- Keep the Android themed/monochrome foreground at its approved 66% scale.
- Centre the final visible bounds independently after scaling.
- Allow no more than 2 px difference between opposite margins at 1080×1080.

This scale accounts for Android's approximately 1.5× adaptive-foreground zoom.
It keeps the mark clear of circular launcher and Settings preview masks.

## Required previews

Before replacing Android resources, verify all variants under:

1. circle;
2. squircle;
3. rounded-square.

The generator enforcing this specification is
`design/app-icon/generate_orange_p4da_adaptive.py`.
