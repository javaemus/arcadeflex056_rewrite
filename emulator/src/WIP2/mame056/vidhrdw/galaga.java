/** *************************************************************************
 *
 * vidhrdw.c
 *
 * Functions to emulate the video hardware of the machine.
 *
 ************************************************************************** */

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */
package WIP2.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;

import static WIP2.common.ptr.*;
import WIP2.common.subArrays.IntArray;
import static WIP2.mame056.common.*;

import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.common.libc.cstring.memset;
import static WIP2.common.libc.expressions.NOT;

public class galaga {

    public static final int MAX_STARS = 250;
    public static final int STARS_COLOR_BASE = 32;

    public static UBytePtr galaga_starcontrol = new UBytePtr();
    static int stars_scroll;
    static int flipscreen;

    static class star {

        public star() {
        }
        public int x, y, col, set;
    }
    static star stars[] = new star[MAX_STARS];

    static {
        for (int k = 0; k < MAX_STARS; k++) {
            stars[k] = new star();
        }
    }
    static int total_stars;

    /**
     * *************************************************************************
     *
     * Convert the color PROMs into a more useable format.
     *
     * Galaga has one 32x8 palette PROM and two 256x4 color lookup table PROMs
     * (one for characters, one for sprites). Only the first 128 bytes of the
     * lookup tables seem to be used. The palette PROM is connected to the RGB
     * output this way:
     *
     * bit 7 -- 220 ohm resistor -- BLUE -- 470 ohm resistor -- BLUE -- 220 ohm
     * resistor -- GREEN -- 470 ohm resistor -- GREEN -- 1 kohm resistor --
     * GREEN -- 220 ohm resistor -- RED -- 470 ohm resistor -- RED bit 0 -- 1
     * kohm resistor -- RED
     *
     **************************************************************************
     */
    static int TOTAL_COLORS(int gfxn) {
        return Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity;
    }
    public static VhConvertColorPromPtr galaga_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;
            for (i = 0; i < 32; i++) {
                int bit0, bit1, bit2;

                bit0 = (color_prom.read(31 - i) >> 0) & 0x01;
                bit1 = (color_prom.read(31 - i) >> 1) & 0x01;
                bit2 = (color_prom.read(31 - i) >> 2) & 0x01;
                palette[3 * i] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
                bit0 = (color_prom.read(31 - i) >> 3) & 0x01;
                bit1 = (color_prom.read(31 - i) >> 4) & 0x01;
                bit2 = (color_prom.read(31 - i) >> 5) & 0x01;
                palette[3 * i + 1] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
                bit0 = 0;
                bit1 = (color_prom.read(31 - i) >> 6) & 0x01;
                bit2 = (color_prom.read(31 - i) >> 7) & 0x01;
                palette[3 * i + 2] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
            }

            color_prom.inc(32);

            /* characters */
            for (i = 0; i < TOTAL_COLORS(0); i++) {
                colortable[Machine.drv.gfxdecodeinfo[0].color_codes_start + i] = (char) (15 - (color_prom.readinc() & 0x0f));
            }

            color_prom.inc(128);

            /* sprites */
            for (i = 0; i < TOTAL_COLORS(1); i++) {
                if (i % 4 == 0) {
                    colortable[Machine.drv.gfxdecodeinfo[1].color_codes_start + i] = (char) 0;
                    /* preserve transparency */
                } else {
                    colortable[Machine.drv.gfxdecodeinfo[1].color_codes_start + i] = (char) (15 - ((color_prom.read() & 0x0f)) + 0x10);
                }

                color_prom.inc();
            }

            color_prom.inc(128);

            /* now the stars */
            for (i = 32; i < 32 + 64; i++) {
                int bits;
                int map[] = {0x00, 0x88, 0xcc, 0xff};

                bits = ((i - 32) >> 0) & 0x03;
                palette[3 * i] = (char) (map[bits]);
                bits = ((i - 32) >> 2) & 0x03;
                palette[3 * i + 1] = (char) (map[bits]);
                bits = ((i - 32) >> 4) & 0x03;
                palette[3 * i + 2] = (char) (map[bits]);
            }
        }
    };

    /**
     * *************************************************************************
     *
     * Start the video hardware emulation.
     *
     **************************************************************************
     */
    public static VhStartPtr galaga_vh_start = new VhStartPtr() {
        public int handler() {
            int generator;
            int x, y;
            int set = 0;

            if (generic_vh_start.handler() != 0) {
                return 1;
            }

            /* precalculate the star background */
 /* this comes from the Galaxian hardware, Galaga is probably different */
            total_stars = 0;
            generator = 0;

            for (y = 0; y <= 255; y++) {
                for (x = 511; x >= 0; x--) {
                    int bit1, bit2;

                    generator <<= 1;
                    bit1 = (~generator >> 17) & 1;
                    bit2 = (generator >> 5) & 1;

                    if ((bit1 ^ bit2) != 0) {
                        generator |= 1;
                    }

                    if (((~generator >> 16) & 1) != 0 && (generator & 0xff) == 0xff) {
                        int color;

                        color = (~(generator >> 8)) & 0x3f;
                        if (color != 0 && total_stars < MAX_STARS) {
                            stars[total_stars].x = x;
                            stars[total_stars].y = y;
                            stars[total_stars].col = Machine.pens.read(color + STARS_COLOR_BASE);
                            stars[total_stars].set = set;
                            if (++set > 3) {
                                set = 0;
                            }

                            total_stars++;
                        }
                    }
                }
            }

            return 0;
        }
    };

    /**
     * *************************************************************************
     *
     * Draw the game screen in the given mame_bitmap. Do NOT call
     * osd_update_display() from this function, it will be called by the main
     * emulation engine.
     *
     **************************************************************************
     */
    public static VhUpdatePtr galaga_vh_screenrefresh = new VhUpdatePtr() {
        public void handler(mame_bitmap bitmap, int full_refresh) {
            int offs;

            if (full_refresh != 0) {
                memset(dirtybuffer, 1, videoram_size[0]);
            }

            /* for every character in the Video RAM, check if it has been modified */
 /* since last time and update it accordingly. */
            for (offs = videoram_size[0] - 1; offs >= 0; offs--) {
                if (dirtybuffer[offs] != 0) {
                    int sx, sy, mx, my;

                    dirtybuffer[offs] = 0;

                    /* Even if Galaga's screen is 28x36, the memory layout is 32x32. We therefore */
 /* have to convert the memory coordinates into screen coordinates. */
 /* Note that 32*32 = 1024, while 28*36 = 1008: therefore 16 bytes of Video RAM */
 /* don't map to a screen position. We don't check that here, however: range */
 /* checking is performed by drawgfx(). */
                    mx = offs % 32;
                    my = offs / 32;

                    if (my <= 1) {
                        sx = my + 34;
                        sy = mx - 2;
                    } else if (my >= 30) {
                        sx = my - 30;
                        sy = mx - 2;
                    } else {
                        sx = mx + 2;
                        sy = my - 2;
                    }

                    if (flip_screen() != 0) {
                        sx = 35 - sx;
                        sy = 27 - sy;
                    }

                    drawgfx(tmpbitmap, Machine.gfx[0],
                            videoram.read(offs),
                            colorram.read(offs),
                            flip_screen(), flip_screen(),
                            8 * sx, 8 * sy,
                            Machine.visible_area, TRANSPARENCY_NONE, 0);
                }
            }

            fillbitmap(bitmap, Machine.pens.read(0), Machine.visible_area);

            /* Draw the sprites. */
            for (offs = 0; offs < spriteram_size[0]; offs += 2) {
                if ((spriteram_3.read(offs + 1) & 2) == 0) {
                    int code, color, flipx, flipy, sx, sy, sfa, sfb;

                    code = spriteram.read(offs);
                    color = spriteram.read(offs + 1);
                    flipx = spriteram_3.read(offs) & 1;
                    flipy = spriteram_3.read(offs) & 2;
                    sx = spriteram_2.read(offs + 1) - 40 + 0x100 * (spriteram_3.read(offs + 1) & 1);
                    sy = 28 * 8 - spriteram_2.read(offs);
                    sfa = 0;
                    sfb = 16;

                    /* this constraint fell out of the old, pre-rotated code automatically */
 /* we need to explicitly add it because of the way we flip Y */
                    if (sy <= -16) {
                        continue;
                    }

                    if (flip_screen() != 0) {
                        flipx = NOT(flipx);
                        flipy = NOT(flipy);
                        sfa = 16;
                        sfb = 0;
                    }

                    if ((spriteram_3.read(offs) & 0x0c) == 0x0c) /* double width, double height */ {
                        drawgfx(bitmap, Machine.gfx[1],
                                code + 2, color, flipx, flipy, sx + sfa, sy - sfa,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                        drawgfx(bitmap, Machine.gfx[1],
                                code, color, flipx, flipy, sx + sfa, sy - sfb,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);

                        drawgfx(bitmap, Machine.gfx[1],
                                code + 3, color, flipx, flipy, sx + sfb, sy - sfa,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                        drawgfx(bitmap, Machine.gfx[1],
                                code + 1, color, flipx, flipy, sx + sfb, sy - sfb,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                    } else if ((spriteram_3.read(offs) & 8) != 0) /* double width */ {
                        drawgfx(bitmap, Machine.gfx[1],
                                code + 2, color, flipx, flipy, sx, sy - sfa,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                        drawgfx(bitmap, Machine.gfx[1],
                                code, color, flipx, flipy, sx, sy - sfb,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                    } else if ((spriteram_3.read(offs) & 4) != 0) /* double height */ {
                        drawgfx(bitmap, Machine.gfx[1],
                                code, color, flipx, flipy, sx + sfa, sy,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                        drawgfx(bitmap, Machine.gfx[1],
                                code + 1, color, flipx, flipy, sx + sfb, sy,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                    } else /* normal */ {
                        drawgfx(bitmap, Machine.gfx[1],
                                code, color, flipx, flipy, sx, sy,
                                Machine.visible_area, TRANSPARENCY_COLOR, 0);
                    }
                }
            }
            /* copy the character mapped graphics */
            copybitmap(bitmap, tmpbitmap, 0, 0, 0, 0, Machine.visible_area, TRANSPARENCY_COLOR, 0);


            /* draw the stars */
            if ((galaga_starcontrol.read(5) & 1) != 0) {
                int bpen;

                bpen = Machine.pens.read(0);
                for (offs = 0; offs < total_stars; offs++) {
                    int x, y;
                    int set;
                    int starset[][] = {{0, 3}, {0, 1}, {2, 3}, {2, 1}};

                    set = ((galaga_starcontrol.read(4) << 1) | galaga_starcontrol.read(3)) & 3;
                    if ((stars[offs].set == starset[set][0])
                            || (stars[offs].set == starset[set][1])) {
                        x = ((stars[offs].x + stars_scroll) % 512) / 2 + 16;
                        y = (stars[offs].y + (stars_scroll + stars[offs].x) / 512) % 256;

                        if (y >= Machine.visible_area.min_y
                                && y <= Machine.visible_area.max_y) {
                            if (read_pixel.handler(bitmap, x & 0xFF, y & 0xFF) == bpen) {
                                plot_pixel.handler(bitmap, x & 0xFF, y & 0xFF, stars[offs].col);
                            }
                        }
                    }
                }
            }
        }
    };

    public static void galaga_vh_interrupt() {
        /* this function is called by galaga_interrupt_1() */
        int s0, s1, s2;
        int speeds[] = {2, 3, 4, 0, -4, -3, -2, 0};

        s0 = galaga_starcontrol.read(0) & 1;
        s1 = galaga_starcontrol.read(1) & 1;
        s2 = galaga_starcontrol.read(2) & 1;

        stars_scroll -= speeds[s0 + s1 * 2 + s2 * 4];
    }

}
