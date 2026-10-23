import os
import math
from PIL import Image, ImageDraw

def create_briefing_icon(size=1024, is_round=False):
    # Canvas
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    scale = size / 1024.0

    # Colors
    c_bg = (10, 10, 12, 255)            # #0A0A0C Pitch Dark
    c_cream = (245, 241, 235, 255)       # #F5F1EB Paper Cream
    c_card = (20, 20, 24, 255)           # #141418 Card surface
    c_amber = (245, 158, 11, 255)        # #F59E0B Warm Amber Prompt
    c_red = (239, 83, 80, 255)           # #EF5350 Security Accent
    c_blue = (96, 165, 250, 255)         # #60A5FA Sky Blue
    c_shadow = (0, 0, 0, 160)

    # Margins and dimensions
    pad = int(54 * scale)
    box_w = size - 2 * pad
    box_h = size - 2 * pad
    radius = int(190 * scale)

    # Base shape background fill
    if is_round:
        draw.ellipse([pad, pad, pad + box_w, pad + box_h], fill=c_bg)
    else:
        # Retro drop shadow
        s_off = int(22 * scale)
        draw.rounded_rectangle([pad + s_off, pad + s_off, pad + box_w + s_off, pad + box_h + s_off],
                               radius=radius, fill=c_shadow)
        # Main body
        draw.rounded_rectangle([pad, pad, pad + box_w, pad + box_h],
                               radius=radius, fill=c_bg)

    # Subtle retro horizontal scanlines
    scan_step = int(36 * scale)
    for y in range(pad + int(85 * scale), pad + box_h - int(85 * scale), scan_step):
        if is_round:
            # calculate circle width at y
            r = box_w / 2.0
            cy = pad + r
            dy = abs(y - cy)
            if dy < r - int(30 * scale):
                dx = math.sqrt(r * r - dy * dy)
                draw.line([(cy - dx + int(30 * scale), y), (cy + dx - int(30 * scale), y)],
                          fill=(40, 40, 52, 90), width=int(2 * scale))
        else:
            draw.line([(pad + int(70 * scale), y), (pad + box_w - int(70 * scale), y)],
                      fill=(40, 40, 52, 90), width=int(2 * scale))

    # Top mini terminal status bar
    top_y = pad + int(115 * scale)
    dot_s = int(24 * scale)
    d_x1 = pad + int(115 * scale)
    draw.rectangle([d_x1, top_y, d_x1 + dot_s, top_y + dot_s], fill=c_red)
    draw.rectangle([d_x1 + int(38 * scale), top_y, d_x1 + int(38 * scale) + dot_s, top_y + dot_s], fill=c_amber)
    draw.rectangle([d_x1 + int(76 * scale), top_y, d_x1 + int(76 * scale) + dot_s, top_y + dot_s], fill=c_blue)

    # Top terminal status frame: "SYS // BRIEFING_OS"
    t_x = pad + box_w - int(330 * scale)
    draw.rectangle([t_x, top_y - int(4 * scale), t_x + int(210 * scale), top_y + int(28 * scale)],
                   outline=c_cream, width=int(3 * scale), fill=c_card)
    # Mini blocky hash marks inside
    for i in range(4):
        hx = t_x + int((20 + i * 24) * scale)
        draw.line([(hx, top_y + int(4 * scale)), (hx + int(10 * scale), top_y + int(20 * scale))],
                  fill=c_cream, width=int(2 * scale))

    # Central Envelope Graphic (Neo-Brutalist Retro Mail Terminal)
    env_w = int(580 * scale)
    env_h = int(390 * scale)
    env_x = (size - env_w) // 2
    env_y = (size - env_h) // 2 + int(30 * scale)

    # Envelope shadow
    draw.rectangle([env_x + int(18 * scale), env_y + int(18 * scale),
                    env_x + env_w + int(18 * scale), env_y + env_h + int(18 * scale)],
                   fill=(18, 18, 24, 255))

    # Envelope main body (Paper Cream)
    draw.rectangle([env_x, env_y, env_x + env_w, env_y + env_h],
                   fill=c_cream, outline=c_bg, width=int(14 * scale))

    # Bottom fold diagonals (classic mail lines)
    b_stroke = int(10 * scale)
    mid_x = env_x + env_w // 2
    mid_y = env_y + int(env_h * 0.62)
    # Left flap to center
    draw.line([(env_x, env_y + env_h), (mid_x, mid_y)], fill=c_bg, width=b_stroke)
    # Right flap to center
    draw.line([(env_x + env_w, env_y + env_h), (mid_x, mid_y)], fill=c_bg, width=b_stroke)

    # Top flap (folded V shape)
    draw.polygon([(env_x, env_y), (env_x + env_w, env_y), (mid_x, mid_y)],
                 fill=(235, 230, 220, 255), outline=c_bg)
    draw.line([(env_x, env_y), (mid_x, mid_y)], fill=c_bg, width=b_stroke)
    draw.line([(env_x + env_w, env_y), (mid_x, mid_y)], fill=c_bg, width=b_stroke)

    # Terminal prompt badge in center of envelope
    badge_w = int(250 * scale)
    badge_h = int(96 * scale)
    badge_x = (size - badge_w) // 2
    badge_y = mid_y - int(126 * scale)

    draw.rectangle([badge_x, badge_y, badge_x + badge_w, badge_y + badge_h],
                   fill=c_bg, outline=c_cream, width=int(6 * scale))

    # Retro prompt chevron ">" in Amber Gold
    ch_x = badge_x + int(32 * scale)
    ch_y = badge_y + int(26 * scale)
    ch_s = int(44 * scale)
    draw.line([(ch_x, ch_y), (ch_x + int(ch_s * 0.7), ch_y + ch_s // 2)], fill=c_amber, width=int(9 * scale))
    draw.line([(ch_x + int(ch_s * 0.7), ch_y + ch_s // 2), (ch_x, ch_y + ch_s)], fill=c_amber, width=int(9 * scale))

    # Pulsing cursor block "█"
    cur_x = ch_x + int(64 * scale)
    cur_y = ch_y + int(4 * scale)
    draw.rectangle([cur_x, cur_y, cur_x + int(30 * scale), cur_y + int(36 * scale)], fill=c_cream)

    # AI Mail Spark / 4-point star on upper right of envelope
    spark_cx = env_x + env_w - int(20 * scale)
    spark_cy = env_y - int(16 * scale)
    sp_r = int(50 * scale)
    draw.polygon([
        (spark_cx, spark_cy - sp_r),
        (spark_cx + int(sp_r * 0.28), spark_cy - int(sp_r * 0.28)),
        (spark_cx + sp_r, spark_cy),
        (spark_cx + int(sp_r * 0.28), spark_cy + int(sp_r * 0.28)),
        (spark_cx, spark_cy + sp_r),
        (spark_cx - int(sp_r * 0.28), spark_cy + int(sp_r * 0.28)),
        (spark_cx - sp_r, spark_cy),
        (spark_cx - int(sp_r * 0.28), spark_cy - int(sp_r * 0.28))
    ], fill=c_amber, outline=c_bg)

    # Bottom status indicator blocks
    bot_y = pad + box_h - int(120 * scale)
    b_seg_w = int(38 * scale)
    b_seg_h = int(16 * scale)
    b_start_x = (size - 10 * int(50 * scale)) // 2
    for i in range(10):
        seg_color = c_amber if i < 3 else (c_cream if i < 7 else (50, 50, 62, 255))
        draw.rectangle([b_start_x + i * int(50 * scale), bot_y,
                        b_start_x + i * int(50 * scale) + b_seg_w, bot_y + b_seg_h],
                       fill=seg_color)

    # Finally, draw outer boundary stroke
    if is_round:
        draw.ellipse([pad, pad, pad + box_w, pad + box_h], outline=c_cream, width=int(22 * scale))
    else:
        draw.rounded_rectangle([pad, pad, pad + box_w, pad + box_h],
                               radius=radius, outline=c_cream, width=int(24 * scale))

    return img

def main():
    res_base = r"D:\apps ideas\ai-mail-assistant\android-app\app\src\main\res"
    densities = [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192)
    ]

    master_square = create_briefing_icon(1024, is_round=False)
    master_round = create_briefing_icon(1024, is_round=True)

    for folder, dim in densities:
        out_dir = os.path.join(res_base, folder)
        os.makedirs(out_dir, exist_ok=True)

        sq = master_square.resize((dim, dim), Image.Resampling.LANCZOS)
        sq.save(os.path.join(out_dir, "ic_launcher.webp"), "WEBP", quality=95)

        rd = master_round.resize((dim, dim), Image.Resampling.LANCZOS)
        rd.save(os.path.join(out_dir, "ic_launcher_round.webp"), "WEBP", quality=95)

        print(f"Generated {folder} ({dim}x{dim})")

    # Save 512x512 preview in brain artifact dir
    artifact_dir = r"C:\Users\mpree\.gemini\antigravity-cli\brain\91680378-0450-42ab-9a95-bfdab2374533"
    sq512 = master_square.resize((512, 512), Image.Resampling.LANCZOS)
    sq512.save(os.path.join(artifact_dir, "briefing_app_icon_square.png"), "PNG")
    rd512 = master_round.resize((512, 512), Image.Resampling.LANCZOS)
    rd512.save(os.path.join(artifact_dir, "briefing_app_icon_round.png"), "PNG")
    print("Saved 512x512 preview icons to artifact dir!")

if __name__ == "__main__":
    main()
