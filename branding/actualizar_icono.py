#!/usr/bin/env python3
"""
Sustituye branding/ic_launcher.png por tu icono (recomendado 1024x1024 PNG)
y ejecuta este script para regenerar todos los tamaños del proyecto.

  python3 branding/actualizar_icono.py
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MASTER = Path(__file__).resolve().parent / "ic_launcher.png"
RES = ROOT / "app" / "src" / "main" / "res"

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def main():
    if not MASTER.exists():
        raise SystemExit(f"No existe {MASTER}. Coloca ahí tu PNG 1024x1024.")

    src = Image.open(MASTER).convert("RGBA")
    print(f"Origen: {MASTER} ({src.size[0]}x{src.size[1]})")

    for folder, size in SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = src.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(out_dir / "ic_launcher.png")
        resized.save(out_dir / "ic_launcher_round.png")
        print(f"  {folder}/ic_launcher.png → {size}x{size}")

    # Adaptive layers: bg solid from corner pixel, fg = full image scaled into safe zone
    draw = RES / "drawable"
    draw.mkdir(parents=True, exist_ok=True)

    # Background: sample corner or use dark default
    bg = Image.new("RGBA", (432, 432), (10, 10, 18, 255))
    bg.save(draw / "ic_launcher_background.png")

    # Foreground: scale master into center ~80% of 432
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    mark = src.resize((320, 320), Image.Resampling.LANCZOS)
    fg.paste(mark, ((432 - 320) // 2, (432 - 320) // 2), mark)
    fg.save(draw / "ic_launcher_foreground.png")
    print("  drawable/ic_launcher_foreground.png + background")
    print("Listo. Recompila la app para ver el icono nuevo.")

if __name__ == "__main__":
    main()
