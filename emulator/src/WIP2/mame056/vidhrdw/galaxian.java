/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */
package WIP2.mame056.vidhrdw;

import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.arcadeflex056.fucPtr.*;

import static WIP2.common.ptr.*;
import static WIP2.common.libc.expressions.*;

import static WIP2.mame056.drivers.scramble.*;

import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;

public class galaxian {

    static rectangle _spritevisiblearea = new rectangle(
            2 * 8 + 1, 32 * 8 - 1,
            2 * 8, 30 * 8 - 1
    );

    static rectangle _spritevisibleareaflipx = new rectangle(
            0 * 8, 30 * 8 - 2,
            2 * 8, 30 * 8 - 1
    );

    static rectangle spritevisiblearea;
    static rectangle spritevisibleareaflipx;

    public static final int STARS_COLOR_BASE = 32;
    public static final int BULLETS_COLOR_BASE = (STARS_COLOR_BASE + 64);
    public static final int BACKGROUND_COLOR_BASE = (BULLETS_COLOR_BASE + 2);

    public static UBytePtr galaxian_videoram = new UBytePtr();
    public static UBytePtr galaxian_spriteram = new UBytePtr();
    public static UBytePtr galaxian_attributesram = new UBytePtr();
    public static UBytePtr galaxian_bulletsram = new UBytePtr();
    public static int[] galaxian_spriteram_size = new int[1];
    public static int[] galaxian_bulletsram_size = new int[1];

    static int mooncrst_gfxextend;
    static int[] pisces_gfxbank = new int[1];
    static int[] jumpbug_gfxbank = new int[5];

    public static abstract interface _modify_charcode {

        public abstract void handler(int[] code, int x);
    }

    static _modify_charcode modify_charcode = null;/* function to call to do character banking */

    public static abstract interface _modify_spritecode {

        public abstract void handler(int[] code, int[] flipx, int[] flipy, int offs);
    }

    static _modify_spritecode modify_spritecode = null;/* function to call to do sprite banking */

    public static abstract interface _modify_color {

        public abstract void handler(int[] code);
    }

    static _modify_color modify_color = null;/* function to call to do modify how the color codes map to the PROM */

    public static abstract interface _modify_ypos {

        public abstract void handler(int[] u8_sy);
    }

    static _modify_ypos modify_ypos = null;/* function to call to do modify how vertical positioning bits are connected */

 /* star circuit */
    public static int STAR_COUNT = 252;

    public static class star {

        public int x = 0, y = 0, color = 0;
    }

    static star[] stars = new star[STAR_COUNT];

    static {
        for (int i = 0; i < STAR_COUNT; i++) {
            stars[i] = new star();
        }
    }

    static int galaxian_stars_on;
    static int stars_scrollpos;
    static int stars_blink_state;
    static Object stars_blink_timer;
    static Object stars_scroll_timer;

    public static abstract interface _draw_stars {

        public abstract void handler(mame_bitmap bitmap);
    }

    static _draw_stars draw_stars = null;/* function to call to draw the star layer */

 /* bullets circuit */
    static int darkplnt_bullet_color;

    public static abstract interface _draw_bullets {

        public abstract void handler(mame_bitmap bitmap, int offs, int x, int y);
    }

    static _draw_bullets draw_bullets = null;/* function to call to draw a bullet */

 /* background circuit */
    static int background_enable;
    static int background_red, background_green, background_blue;

    public static abstract interface _draw_background {

        public abstract void handler(mame_bitmap bitmap);
    }

    static _draw_background draw_background = null;/* function to call to draw the background */


    /**
     * *************************************************************************
     *
     * Convert the color PROMs into a more useable format.
     *
     * Galaxian has one 32 bytes palette PROM, connected to the RGB output this
     * way:
     *
     * bit 7 -- 220 ohm resistor -- BLUE -- 470 ohm resistor -- BLUE -- 220 ohm
     * resistor -- GREEN -- 470 ohm resistor -- GREEN -- 1 kohm resistor --
     * GREEN -- 220 ohm resistor -- RED -- 470 ohm resistor -- RED bit 0 -- 1
     * kohm resistor -- RED
     *
     * The output of the background star generator is connected this way:
     *
     * bit 5 -- 100 ohm resistor -- BLUE -- 150 ohm resistor -- BLUE -- 100 ohm
     * resistor -- GREEN -- 150 ohm resistor -- GREEN -- 100 ohm resistor -- RED
     * bit 0 -- 150 ohm resistor -- RED
     *
     * The blue background in Scramble and other games goes through a 390 ohm
     * resistor.
     *
     * The bullet RGB outputs go through 100 ohm resistors.
     *
     * The RGB outputs have a 470 ohm pull-down each.
     *
     **************************************************************************
     */
    public static VhConvertColorPromPtr galaxian_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;

            /* first, the character/sprite palette */
            int[] p_ptr = new int[1];
            for (i = 0; i < 32; i++) {
                int bit0, bit1, bit2;

                /* red component */
                bit0 = (color_prom.read() >> 0) & 0x01;
                bit1 = (color_prom.read() >> 1) & 0x01;
                bit2 = (color_prom.read() >> 2) & 0x01;
                palette[p_ptr[0]++] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
                /* green component */
                bit0 = (color_prom.read() >> 3) & 0x01;
                bit1 = (color_prom.read() >> 4) & 0x01;
                bit2 = (color_prom.read() >> 5) & 0x01;
                palette[p_ptr[0]++] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
                /* blue component */
                bit0 = (color_prom.read() >> 6) & 0x01;
                bit1 = (color_prom.read() >> 7) & 0x01;
                palette[p_ptr[0]++] = (char) (0x4f * bit0 + 0xa8 * bit1);

                color_prom.inc();
            }

            galaxian_init_stars(palette, p_ptr);

            /* bullets - yellow and white */
            palette[p_ptr[0]++] = (char) (0xef);
            palette[p_ptr[0]++] = (char) (0xef);
            palette[p_ptr[0]++] = (char) (0x00);

            palette[p_ptr[0]++] = (char) (0xef);
            palette[p_ptr[0]++] = (char) (0xef);
            palette[p_ptr[0]++] = (char) (0xef);
        }
    };

    public static VhConvertColorPromPtr scramble_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            galaxian_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /* blue background - 390 ohm resistor */
            palette[(BACKGROUND_COLOR_BASE * 3) + 0] = 0;
            palette[(BACKGROUND_COLOR_BASE * 3) + 1] = 0;
            palette[(BACKGROUND_COLOR_BASE * 3) + 2] = 0x56;
        }
    };

    public static VhConvertColorPromPtr moonwar_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            scramble_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /* wire mod to connect the bullet blue output to the 220 ohm resistor */
            palette[BULLETS_COLOR_BASE * 3 + 2] = 0x97;
        }
    };

    public static VhConvertColorPromPtr turtles_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;

            galaxian_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /*  The background color generator is connected this way:
	
			RED   - 390 ohm resistor
			GREEN - 470 ohm resistor
			BLUE  - 390 ohm resistor */
            for (i = 0; i < 8; i++) {
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 0] = (i & 0x01) != 0 ? (char) 0x55 : 0x00;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 1] = (i & 0x02) != 0 ? (char) 0x47 : 0x00;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 2] = (i & 0x04) != 0 ? (char) 0x55 : 0x00;
            }
        }
    };

    public static VhConvertColorPromPtr stratgyx_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;

            galaxian_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /*  The background color generator is connected this way:
	
			RED   - 270 ohm resistor
			GREEN - 560 ohm resistor
			BLUE  - 470 ohm resistor */
            for (i = 0; i < 8; i++) {
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 0] = (i & 0x01) != 0 ? (char) 0x7c : 0x00;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 1] = (i & 0x02) != 0 ? (char) 0x3c : 0x00;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 2] = (i & 0x04) != 0 ? (char) 0x47 : 0x00;
            }
        }
    };

    public static VhConvertColorPromPtr frogger_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            galaxian_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /* blue background - 470 ohm resistor */
            palette[(BACKGROUND_COLOR_BASE * 3) + 0] = 0;
            palette[(BACKGROUND_COLOR_BASE * 3) + 1] = 0;
            palette[(BACKGROUND_COLOR_BASE * 3) + 2] = 0x47;
        }
    };

    /**
     * *************************************************************************
     *
     * Convert the color PROMs into a more useable format.
     *
     * Dark Planet has one 32 bytes palette PROM, connected to the RGB output
     * this way:
     *
     * bit 5 -- 220 ohm resistor -- BLUE -- 470 ohm resistor -- BLUE -- 1 kohm
     * resistor -- BLUE -- 220 ohm resistor -- RED -- 470 ohm resistor -- RED
     * bit 0 -- 1 kohm resistor -- RED
     *
     * The bullet RGB outputs go through 100 ohm resistors.
     *
     * The RGB outputs have a 470 ohm pull-down each.
     *
     **************************************************************************
     */
    public static VhConvertColorPromPtr darkplnt_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;

            /* first, the character/sprite palette */
            int p_ptr = 0;
            for (i = 0; i < 32; i++) {
                int bit0, bit1, bit2;

                /* red component */
                bit0 = (color_prom.read() >> 0) & 0x01;
                bit1 = (color_prom.read() >> 1) & 0x01;
                bit2 = (color_prom.read() >> 2) & 0x01;
                palette[p_ptr++] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
                /* green component */
                palette[p_ptr++] = (char) (0x00);
                /* blue component */
                bit0 = (color_prom.read() >> 3) & 0x01;
                bit1 = (color_prom.read() >> 4) & 0x01;
                bit2 = (color_prom.read() >> 5) & 0x01;
                palette[p_ptr++] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);

                color_prom.inc();
            }

            /* bullets - red and blue */
            palette[p_ptr++] = (char) (0xef);
            palette[p_ptr++] = (char) (0x00);
            palette[p_ptr++] = (char) (0x00);

            palette[p_ptr++] = (char) (0x00);
            palette[p_ptr++] = (char) (0x00);
            palette[p_ptr++] = (char) (0xef);
        }
    };

    public static VhConvertColorPromPtr minefld_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;

            galaxian_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /* set up background colors */
 /* graduated blue */
            for (i = 0; i < 128; i++) {
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 0] = 0;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 1] = (char) i;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 2] = (char) (i * 2);
            }

            /* graduated brown */
            for (i = 0; i < 128; i++) {
                palette[(BACKGROUND_COLOR_BASE + 128 + i) * 3 + 0] = (char) (i * 1.5);
                palette[(BACKGROUND_COLOR_BASE + 128 + i) * 3 + 1] = (char) (i * 0.75);
                palette[(BACKGROUND_COLOR_BASE + 128 + i) * 3 + 2] = (char) (i / 2);
            }
        }
    };

    public static VhConvertColorPromPtr rescue_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;

            galaxian_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /* set up background colors */
 /* graduated blue */
            for (i = 0; i < 128; i++) {
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 0] = 0;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 1] = (char) i;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 2] = (char) (i * 2);
            }
        }
    };

    public static VhConvertColorPromPtr mariner_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;

            galaxian_vh_convert_color_prom.handler(palette, colortable, color_prom);

            /* set up background colors */
 /* 16 shades of blue - the 4 bits are connected to the following resistors:
	
			bit 0 -- 4.7 kohm resistor
				  -- 2.2 kohm resistor
				  -- 1   kohm resistor
			bit 0 -- .47 kohm resistor */
            for (i = 0; i < 16; i++) {
                int bit0, bit1, bit2, bit3;

                bit0 = (i >> 0) & 0x01;
                bit1 = (i >> 1) & 0x01;
                bit2 = (i >> 2) & 0x01;
                bit3 = (i >> 3) & 0x01;

                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 0] = 0;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 1] = 0;
                palette[(BACKGROUND_COLOR_BASE + i) * 3 + 2] = (char) (0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
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
    public static VhStartPtr galaxian_plain_vh_start = new VhStartPtr() {
        public int handler() {

            modify_charcode = null;
            modify_spritecode = null;
            modify_color = null;
            modify_ypos = null;

            mooncrst_gfxextend = 0;

            draw_bullets = null;

            draw_background = null;
            background_enable = 0;
            background_blue = 0;
            background_red = 0;
            background_green = 0;

            flip_screen_x_set(0);
            flip_screen_y_set(0);

            /* all the games except New Sinbad 7 clip the sprites at the top of the screen,
		   New Sinbad 7 does it at the bottom */
            if (Machine.gamedrv == driver_newsin7) {
                spritevisiblearea = _spritevisibleareaflipx;
                spritevisibleareaflipx = _spritevisiblearea;
            } else {
                spritevisiblearea = _spritevisiblearea;
                spritevisibleareaflipx = _spritevisibleareaflipx;
            }

            return 0;
        }
    };

    public static VhStartPtr galaxian_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_stars = galaxian_draw_stars;

            draw_bullets = galaxian_draw_bullets;

            return ret;
        }
    };

    public static VhStartPtr mooncrst_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_vh_start.handler();

            modify_charcode = mooncrst_modify_charcode;
            modify_spritecode = mooncrst_modify_spritecode;

            return ret;
        }
    };

    public static VhStartPtr moonqsr_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_vh_start.handler();

            modify_charcode = moonqsr_modify_charcode;
            modify_spritecode = moonqsr_modify_spritecode;

            return ret;
        }
    };

    public static VhStartPtr pisces_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_vh_start.handler();

            modify_charcode = pisces_modify_charcode;
            modify_spritecode = pisces_modify_spritecode;

            return ret;
        }
    };

    public static VhStartPtr gteikob2_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = pisces_vh_start.handler();

            draw_bullets = gteikob2_draw_bullets;

            return ret;
        }
    };

    public static VhStartPtr batman2_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_vh_start.handler();

            modify_charcode = batman2_modify_charcode;
            modify_spritecode = batman2_modify_spritecode;

            return ret;
        }
    };

    public static VhStartPtr scramble_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_stars = scramble_draw_stars;

            draw_bullets = scramble_draw_bullets;

            draw_background = scramble_draw_background;

            return ret;
        }
    };

    public static VhStartPtr turtles_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_background = turtles_draw_background;

            return ret;
        }
    };

    public static VhStartPtr theend_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_vh_start.handler();

            draw_bullets = theend_draw_bullets;

            return ret;
        }
    };

    public static VhStartPtr darkplnt_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_bullets = darkplnt_draw_bullets;

            return ret;
        }
    };

    public static VhStartPtr rescue_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = scramble_vh_start.handler();

            draw_stars = rescue_draw_stars;

            draw_background = rescue_draw_background;

            return ret;
        }
    };

    public static VhStartPtr minefld_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = scramble_vh_start.handler();

            draw_stars = rescue_draw_stars;

            draw_background = minefld_draw_background;

            return ret;
        }
    };

    public static VhStartPtr stratgyx_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_background = stratgyx_draw_background;

            return ret;
        }
    };

    public static VhStartPtr ckongs_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = scramble_vh_start.handler();

            modify_spritecode = ckongs_modify_spritecode;

            return ret;
        }
    };

    public static VhStartPtr calipso_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_bullets = scramble_draw_bullets;

            draw_background = scramble_draw_background;

            modify_spritecode = calipso_modify_spritecode;

            return ret;
        }
    };

    public static VhStartPtr mariner_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_stars = mariner_draw_stars;

            draw_bullets = scramble_draw_bullets;

            draw_background = mariner_draw_background;

            modify_charcode = mariner_modify_charcode;

            return ret;
        }
    };

    public static VhStartPtr froggers_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = galaxian_plain_vh_start.handler();

            draw_background = frogger_draw_background;

            return ret;
        }
    };

    public static VhStartPtr frogger_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = froggers_vh_start.handler();

            modify_color = frogger_modify_color;
            modify_ypos = frogger_modify_ypos;

            return ret;
        }
    };

    public static VhStartPtr froggrmc_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = froggers_vh_start.handler();

            modify_color = frogger_modify_color;

            return ret;
        }
    };

    public static VhStartPtr jumpbug_vh_start = new VhStartPtr() {
        public int handler() {
            int ret = scramble_vh_start.handler();

            draw_stars = jumpbug_draw_stars;

            modify_charcode = jumpbug_modify_charcode;
            modify_spritecode = jumpbug_modify_spritecode;

            return ret;
        }
    };

    public static WriteHandlerPtr galaxian_videoram_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            galaxian_videoram.write(offset, data);
        }
    };

    public static ReadHandlerPtr galaxian_videoram_r = new ReadHandlerPtr() {
        public int handler(int offset) {
            return galaxian_videoram.read(offset);
        }
    };

    public static WriteHandlerPtr galaxian_flip_screen_x_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            flip_screen_x_set(data);
        }
    };

    public static WriteHandlerPtr galaxian_flip_screen_y_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            flip_screen_y_set(data);
        }
    };

    public static WriteHandlerPtr gteikob2_flip_screen_x_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            flip_screen_x_set(~data & 0x01);
        }
    };

    public static WriteHandlerPtr gteikob2_flip_screen_y_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            flip_screen_y_set(~data & 0x01);
        }
    };

    public static WriteHandlerPtr scramble_background_enable_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            background_enable = data & 0x01;
        }
    };

    public static WriteHandlerPtr scramble_background_red_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            background_red = data & 0x01;
        }
    };

    public static WriteHandlerPtr scramble_background_green_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            background_green = data & 0x01;
        }
    };

    public static WriteHandlerPtr scramble_background_blue_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            background_blue = data & 0x01;
        }
    };

    public static WriteHandlerPtr galaxian_stars_enable_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            galaxian_stars_on = data & 0x01;

            if (galaxian_stars_on == 0) {
                stars_scrollpos = 0;
            }
        }
    };

    public static WriteHandlerPtr darkplnt_bullet_color_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            darkplnt_bullet_color = data & 0x01;
        }
    };

    public static WriteHandlerPtr mooncrst_gfxextend_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            if (data != 0) {
                mooncrst_gfxextend |= (1 << offset);
            } else {
                mooncrst_gfxextend &= ~(1 << offset);
            }
        }
    };

    public static WriteHandlerPtr mooncrgx_gfxextend_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            /* for the Moon Cresta bootleg on Galaxian H/W the gfx_extend is
		 located at 0x6000-0x6002.  Also, 0x6000 and 0x6001 are reversed. */
            if (offset == 1) {
                offset = 0;
            } else if (offset == 0) {
                offset = 1;
            }
            mooncrst_gfxextend_w.handler(offset, data);
        }
    };

    public static WriteHandlerPtr pisces_gfxbank_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            set_vh_global_attribute(pisces_gfxbank, data & 0x01);
        }
    };

    public static WriteHandlerPtr jumpbug_gfxbank_w = new WriteHandlerPtr() {
        public void handler(int offset, int data) {
            set_vh_global_attribute(jumpbug_gfxbank, offset, data & 0x01);
        }
    };

    /* character banking functions */
    public static _modify_charcode mooncrst_modify_charcode = new _modify_charcode() {
        public void handler(int[] code, int x) {
            if (((mooncrst_gfxextend & 0x04) != 0) && ((code[0] & 0xc0) == 0x80)) {
                code[0] = (code[0] & 0x3f) | (mooncrst_gfxextend << 6);
            }
        }
    };

    public static _modify_charcode moonqsr_modify_charcode = new _modify_charcode() {
        public void handler(int[] code, int x) {
            if ((galaxian_attributesram.read((x << 1) | 1) & 0x20) != 0) {
                code[0] += 256;
            }

            mooncrst_modify_charcode.handler(code, x);
        }
    };

    public static _modify_charcode pisces_modify_charcode = new _modify_charcode() {
        public void handler(int[] code, int x) {
            if (pisces_gfxbank[0] != 0) {
                code[0] += 256;
            }
        }
    };

    public static _modify_charcode batman2_modify_charcode = new _modify_charcode() {
        public void handler(int[] code, int x) {
            if (((code[0] & 0x80) != 0) && (pisces_gfxbank[0] != 0)) {
                code[0] += 256;
            }
        }
    };

    public static _modify_charcode mariner_modify_charcode = new _modify_charcode() {
        public void handler(int[] code, int x) {
            UBytePtr prom;

            /* bit 0 of the PROM controls character banking */
            prom = memory_region(REGION_USER2);

            if ((prom.read(x) & 0x01) != 0) {
                code[0] += 256;
            }
        }
    };

    public static _modify_charcode jumpbug_modify_charcode = new _modify_charcode() {
        public void handler(int[] code, int x) {
            if (((code[0] & 0xc0) == 0x80)
                    && (jumpbug_gfxbank[2] & 0x01) != 0) {
                code[0] += 128 + ((jumpbug_gfxbank[0] & 0x01) << 6)
                        + ((jumpbug_gfxbank[1] & 0x01) << 7)
                        + ((~jumpbug_gfxbank[4] & 0x01) << 8);
            }
        }
    };

    /* sprite banking functions */
    public static _modify_spritecode mooncrst_modify_spritecode = new _modify_spritecode() {
        public void handler(int[] code, int[] flipx, int[] flipy, int offs) {
            {
                if (((mooncrst_gfxextend & 0x04) != 0) && (code[0] & 0x30) == 0x20) {
                    code[0] = (code[0] & 0x0f) | (mooncrst_gfxextend << 4);
                }
            }
        }
    };

    public static _modify_spritecode moonqsr_modify_spritecode = new _modify_spritecode() {
        public void handler(int[] code, int[] flipx, int[] flipy, int offs) {
            {
                if ((galaxian_spriteram.read(offs + 2) & 0x20) != 0) {
                    code[0] += 64;
                }

                mooncrst_modify_spritecode.handler(code, flipx, flipy, offs);
            }
        }
    };

    public static _modify_spritecode ckongs_modify_spritecode = new _modify_spritecode() {
        public void handler(int[] code, int[] flipx, int[] flipy, int offs) {
            {
                if ((galaxian_spriteram.read(offs + 2) & 0x10) != 0) {
                    code[0] += 64;
                }
            }
        }
    };

    public static _modify_spritecode calipso_modify_spritecode = new _modify_spritecode() {
        public void handler(int[] code, int[] flipx, int[] flipy, int offs) {
            {
                /* No flips */
                code[0] = galaxian_spriteram.read(offs + 1);
                flipx[0] = 0;
                flipy[0] = 0;
            }
        }
    };

    public static _modify_spritecode pisces_modify_spritecode = new _modify_spritecode() {
        public void handler(int[] code, int[] flipx, int[] flipy, int offs) {
            {
                if (pisces_gfxbank[0] != 0) {
                    code[0] += 64;
                }
            }
        }
    };

    public static _modify_spritecode batman2_modify_spritecode = new _modify_spritecode() {
        public void handler(int[] code, int[] flipx, int[] flipy, int offs) {
            {
                /* only the upper 64 sprites are used */

                code[0] += 64;
            }
        }
    };

    public static _modify_spritecode jumpbug_modify_spritecode = new _modify_spritecode() {
        public void handler(int[] code, int[] flipx, int[] flipy, int offs) {
            {
                if (((code[0] & 0x30) == 0x20)
                        && (jumpbug_gfxbank[2] & 0x01) != 0) {
                    code[0] += 32 + ((jumpbug_gfxbank[0] & 0x01) << 4)
                            + ((jumpbug_gfxbank[1] & 0x01) << 5)
                            + ((~jumpbug_gfxbank[4] & 0x01) << 6);
                }
            }
        }
    };

    /* color PROM mapping functions */
    public static _modify_color frogger_modify_color = new _modify_color() {
        public void handler(int[] color) {
            color[0] = ((color[0] >> 1) & 0x03) | ((color[0] << 2) & 0x04);
        }
    };

    /* y position mapping functions */
    public static _modify_ypos frogger_modify_ypos = new _modify_ypos() {
        public void handler(int[] sy) {
            sy[0] = ((sy[0] << 4) | (sy[0] >> 4)) & 0xFF;
        }
    };

    /* bullet drawing functions */
    public static _draw_bullets galaxian_draw_bullets = new _draw_bullets() {
        public void handler(mame_bitmap bitmap, int offs, int x, int y) {
            int i;

            for (i = 0; i < 4; i++) {
                x--;

                if (x >= Machine.visible_area.min_x
                        && x <= Machine.visible_area.max_x) {
                    int color;

                    /* yellow missile, white shells (this is the terminology on the schematics) */
                    color = ((offs == 7 * 4) ? BULLETS_COLOR_BASE : BULLETS_COLOR_BASE + 1);

                    plot_pixel.handler(bitmap, x, y, Machine.pens.read(color));
                }
            }
        }
    };
    public static _draw_bullets gteikob2_draw_bullets = new _draw_bullets() {
        public void handler(mame_bitmap bitmap, int offs, int x, int y) {
            galaxian_draw_bullets.handler(bitmap, offs, 260 - x, y);
        }
    };

    public static _draw_bullets scramble_draw_bullets = new _draw_bullets() {
        public void handler(mame_bitmap bitmap, int offs, int x, int y) {
            if (flip_screen_x[0] != 0) {
                x++;
            }

            x = x - 6;

            if (x >= Machine.visible_area.min_x
                    && x <= Machine.visible_area.max_x) {
                /* yellow bullets */
                plot_pixel.handler(bitmap, x, y, Machine.pens.read(BULLETS_COLOR_BASE));
            }
        }
    };

    public static _draw_bullets darkplnt_draw_bullets = new _draw_bullets() {
        public void handler(mame_bitmap bitmap, int offs, int x, int y) {
            if (flip_screen_x[0] != 0) {
                x++;
            }

            x = x - 6;

            if (x >= Machine.visible_area.min_x
                    && x <= Machine.visible_area.max_x) {
                plot_pixel.handler(bitmap, x, y, Machine.pens.read(32 + darkplnt_bullet_color));
            }
        }
    };

    public static _draw_bullets theend_draw_bullets = new _draw_bullets() {
        public void handler(mame_bitmap bitmap, int offs, int x, int y) {
            int i;

            /* same as Galaxian, but all bullets are yellow */
            for (i = 0; i < 4; i++) {
                x--;

                if (x >= Machine.visible_area.min_x
                        && x <= Machine.visible_area.max_x) {
                    plot_pixel.handler(bitmap, x, y, Machine.pens.read(BULLETS_COLOR_BASE));
                }
            }
        }
    };

    /* background drawing functions */
    public static _draw_background scramble_draw_background = new _draw_background() {
        public void handler(mame_bitmap bitmap) {
            if (background_enable != 0) {
                fillbitmap(bitmap, Machine.pens.read(BACKGROUND_COLOR_BASE), Machine.visible_area);
            } else {
                fillbitmap(bitmap, Machine.pens.read(0), Machine.visible_area);
            }
        }
    };

    public static _draw_background turtles_draw_background = new _draw_background() {
        public void handler(mame_bitmap bitmap) {
            int color = (background_blue << 2) | (background_green << 1) | background_red;

            fillbitmap(bitmap, Machine.pens.read(BACKGROUND_COLOR_BASE + color), Machine.visible_area);
        }
    };

    public static _draw_background frogger_draw_background = new _draw_background() {
        public void handler(mame_bitmap bitmap) {
            /* color split point verified on real machine */
            if (flip_screen_x[0] != 0) {
                plot_box.handler(bitmap, 0, 0, 128, 256, Machine.pens.read(0));
                plot_box.handler(bitmap, 128, 0, 128, 256, Machine.pens.read(BACKGROUND_COLOR_BASE));
            } else {
                plot_box.handler(bitmap, 0, 0, 128, 256, Machine.pens.read(BACKGROUND_COLOR_BASE));
                plot_box.handler(bitmap, 128, 0, 128, 256, Machine.pens.read(0));
            }
        }
    };
    public static _draw_background stratgyx_draw_background = new _draw_background() {
        public void handler(mame_bitmap bitmap) {
            int x;
            UBytePtr prom;

            /* the background PROM is connected the following way:
	
		   bit 0 = 0 enables the blue gun if BCB is asserted
		   bit 1 = 0 enables the red gun if BCR is asserted and
		             the green gun if BCG is asserted
		   bits 2-7 are unconnected */
            prom = memory_region(REGION_USER1);

            for (x = 0; x < 32; x++) {
                int sx, color;

                color = 0;

                if (((~prom.read(x) & 0x02) != 0) && (background_red != 0)) {
                    color |= 0x01;
                }
                if (((~prom.read(x) & 0x02) != 0) && (background_green != 0)) {
                    color |= 0x02;
                }
                if (((~prom.read(x) & 0x01) != 0) && (background_blue != 0)) {
                    color |= 0x04;
                }

                if (flip_screen_x[0] != 0) {
                    sx = 8 * (31 - x);
                } else {
                    sx = 8 * x;
                }

                plot_box.handler(bitmap, sx, 0, 8, 256, Machine.pens.read(BACKGROUND_COLOR_BASE + color));
            }
        }
    };

    public static _draw_background minefld_draw_background = new _draw_background() {
        public void handler(mame_bitmap bitmap) {
            if (background_enable != 0) {
                int x;

                for (x = 0; x < 128; x++) {
                    plot_box.handler(bitmap, x, 0, 1, 256, Machine.pens.read(BACKGROUND_COLOR_BASE + x));
                }

                for (x = 0; x < 120; x++) {
                    plot_box.handler(bitmap, x + 128, 0, 1, 256, Machine.pens.read(BACKGROUND_COLOR_BASE + x + 128));
                }

                plot_box.handler(bitmap, 248, 0, 16, 256, Machine.pens.read(BACKGROUND_COLOR_BASE));
            } else {
                fillbitmap(bitmap, Machine.pens.read(0), Machine.visible_area);
            }
        }
    };

    public static _draw_background rescue_draw_background = new _draw_background() {
        public void handler(mame_bitmap bitmap) {
            if (background_enable != 0) {
                int x;

                for (x = 0; x < 128; x++) {
                    plot_box.handler(bitmap, x, 0, 1, 256, Machine.pens.read(BACKGROUND_COLOR_BASE + x));
                }

                for (x = 0; x < 120; x++) {
                    plot_box.handler(bitmap, x + 128, 0, 1, 256, Machine.pens.read(BACKGROUND_COLOR_BASE + x + 8));
                }

                plot_box.handler(bitmap, 248, 0, 16, 256, Machine.pens.read(BACKGROUND_COLOR_BASE));
            } else {
                fillbitmap(bitmap, Machine.pens.read(0), Machine.visible_area);
            }
        }
    };

    public static _draw_background mariner_draw_background = new _draw_background() {
        public void handler(mame_bitmap bitmap) {
            int x;
            UBytePtr prom;

            /* the background PROM contains the color codes for each 8 pixel
		   line (column) of the screen.  The first 0x20 bytes for unflipped,
		   and the 2nd 0x20 bytes for flipped screen. */
            prom = memory_region(REGION_USER1);

            if (flip_screen_x[0] != 0) {
                for (x = 0; x < 32; x++) {
                    int color;

                    if (x == 0) {
                        color = 0;
                    } else {
                        color = prom.read(0x20 + x - 1);
                    }

                    plot_box.handler(bitmap, 8 * (31 - x), 0, 8, 256, Machine.pens.read(BACKGROUND_COLOR_BASE + color));
                }
            } else {
                for (x = 0; x < 32; x++) {
                    int color;

                    if (x == 31) {
                        color = 0;
                    } else {
                        color = prom.read(x + 1);
                    }

                    plot_box.handler(bitmap, 8 * x, 0, 8, 256, Machine.pens.read(BACKGROUND_COLOR_BASE + color));
                }
            }
        }
    };

    /* star drawing functions */
    public static void galaxian_init_stars(char[] palette, int[] p_ptr) {
        int i;
        int total_stars;
        int/*UINT32*/ generator;
        int x, y;

        draw_stars = null;
        galaxian_stars_on = 0;
        stars_blink_state = 0;
        if (stars_blink_timer != null) {
            timer_remove(stars_blink_timer);
        }
        if (stars_scroll_timer != null) {
            timer_remove(stars_scroll_timer);
        }
        stars_blink_timer = 0;
        stars_scroll_timer = 0;

        for (i = 0; i < 64; i++) {
            int bits;
            int map[] = {0x00, 0x88, 0xcc, 0xff};

            bits = (i >> 0) & 0x03;
            palette[p_ptr[0]++] = (char) map[bits];
            bits = (i >> 2) & 0x03;
            palette[p_ptr[0]++] = (char) map[bits];
            bits = (i >> 4) & 0x03;
            palette[p_ptr[0]++] = (char) map[bits];
        }

        /* precalculate the star background */
        total_stars = 0;
        generator = 0;

        for (y = 255; y >= 0; y--) {
            for (x = 511; x >= 0; x--) {
                int/*UINT32*/ bit0;

                bit0 = ((~generator >> 16) & 0x01) ^ ((generator >> 4) & 0x01);

                generator = (generator << 1) | bit0;

                if ((((~generator >> 16) & 0x01) != 0) && (generator & 0xff) == 0xff) {
                    int color;

                    color = (~(generator >> 8)) & 0x3f;
                    if (color != 0) {
                        stars[total_stars].x = x;
                        stars[total_stars].y = y;
                        stars[total_stars].color = color;

                        total_stars++;
                    }
                }
            }
        }

        if (total_stars != STAR_COUNT) {
            logerror("total_stars = %d, STAR_COUNT = %d\n", total_stars, STAR_COUNT);
            //exit(1);
        }
    }

    static void plot_star(mame_bitmap bitmap, int x, int y, int color) {
        if (y < Machine.visible_area.min_y
                || y > Machine.visible_area.max_y
                || x < Machine.visible_area.min_x
                || x > Machine.visible_area.max_x) {
            return;
        }

        if (flip_screen_x[0] != 0) {
            x = 255 - x;
        }
        if (flip_screen_y[0] != 0) {
            y = 255 - y;
        }

        plot_pixel.handler(bitmap, x, y, Machine.pens.read(STARS_COLOR_BASE + color));
    }

    public static _draw_stars galaxian_draw_stars = new _draw_stars() {
        public void handler(mame_bitmap bitmap) {
            int offs;

            if (stars_scroll_timer == null) {
                start_stars_scroll_timer();
            }

            for (offs = 0; offs < STAR_COUNT; offs++) {
                int x, y;

                x = ((stars[offs].x + stars_scrollpos) & 0x01ff) >> 1;
                y = (stars[offs].y + ((stars_scrollpos + stars[offs].x) >> 9)) & 0xff;

                if (((y & 0x01) ^ ((x >> 3) & 0x01)) != 0) {
                    plot_star(bitmap, x, y, stars[offs].color);
                }
            }
        }
    };

    public static _draw_stars scramble_draw_stars = new _draw_stars() {
        public void handler(mame_bitmap bitmap) {
            int offs;

            if (stars_blink_timer == null) {
                start_stars_blink_timer(100000, 10000, 0.00001);
            }

            for (offs = 0; offs < STAR_COUNT; offs++) {
                int x, y;

                x = stars[offs].x >> 1;
                y = stars[offs].y;

                if ((((y & 0x01) ^ ((x >> 3) & 0x01))) != 0) {
                    /* determine when to skip plotting */
                    switch (stars_blink_state & 0x03) {
                        case 0:
                            if ((stars[offs].color & 0x01) == 0) {
                                continue;
                            }
                            break;
                        case 1:
                            if ((stars[offs].color & 0x04) == 0) {
                                continue;
                            }
                            break;
                        case 2:
                            if ((stars[offs].y & 0x02) == 0) {
                                continue;
                            }
                            break;
                        case 3:
                            /* always plot */
                            break;
                    }

                    plot_star(bitmap, x, y, stars[offs].color);
                }
            }
        }
    };

    public static _draw_stars rescue_draw_stars = new _draw_stars() {
        public void handler(mame_bitmap bitmap) {
            int offs;

            /* same as Scramble, but only top (left) half of screen */
            if (stars_blink_timer == null) {
                start_stars_blink_timer(100000, 10000, 0.00001);
            }

            for (offs = 0; offs < STAR_COUNT; offs++) {
                int x, y;

                x = stars[offs].x >> 1;
                y = stars[offs].y;

                if ((x < 128) && (((y & 0x01) ^ ((x >> 3) & 0x01)) != 0)) {
                    /* determine when to skip plotting */
                    switch (stars_blink_state & 0x03) {
                        case 0:
                            if ((stars[offs].color & 0x01) == 0) {
                                continue;
                            }
                            break;
                        case 1:
                            if ((stars[offs].color & 0x04) == 0) {
                                continue;
                            }
                            break;
                        case 2:
                            if ((stars[offs].y & 0x02) == 0) {
                                continue;
                            }
                            break;
                        case 3:
                            /* always plot */
                            break;
                    }

                    plot_star(bitmap, x, y, stars[offs].color);
                }
            }
        }
    };

    public static _draw_stars mariner_draw_stars = new _draw_stars() {
        public void handler(mame_bitmap bitmap) {
            int offs;
            UBytePtr prom;

            if (stars_scroll_timer == null) {
                start_stars_scroll_timer();
            }

            /* bit 2 of the PROM controls star visibility */
            prom = memory_region(REGION_USER2);

            for (offs = 0; offs < STAR_COUNT; offs++) {
                int x, y;

                x = ((stars[offs].x + -stars_scrollpos) & 0x01ff) >> 1;
                y = (stars[offs].y + ((-stars_scrollpos + stars[offs].x) >> 9)) & 0xff;

                if (((y & 0x01) ^ ((x >> 3) & 0x01)) != 0) {
                    if ((prom.read((x / 8 + 1) & 0x1f) & 0x04) != 0) {
                        plot_star(bitmap, x, y, stars[offs].color);
                    }
                }
            }
        }
    };

    public static _draw_stars jumpbug_draw_stars = new _draw_stars() {
        public void handler(mame_bitmap bitmap) {
            int offs;

            if (stars_blink_timer == null) {
                start_stars_blink_timer(100000, 10000, 0.00001);
            }

            if (stars_scroll_timer == null) {
                start_stars_scroll_timer();
            }

            for (offs = 0; offs < STAR_COUNT; offs++) {
                int x, y;

                x = stars[offs].x >> 1;
                y = stars[offs].y;

                /* determine when to skip plotting */
                if (((y & 0x01) ^ ((x >> 3) & 0x01)) != 0) {
                    switch (stars_blink_state & 0x03) {
                        case 0:
                            if ((stars[offs].color & 0x01) == 0) {
                                continue;
                            }
                            break;
                        case 1:
                            if ((stars[offs].color & 0x04) == 0) {
                                continue;
                            }
                            break;
                        case 2:
                            if ((stars[offs].y & 0x02) == 0) {
                                continue;
                            }
                            break;
                        case 3:
                            /* always plot */
                            break;
                    }

                    x = ((stars[offs].x + stars_scrollpos) & 0x01ff) >> 1;
                    y = (stars[offs].y + ((stars_scrollpos + stars[offs].x) >> 9)) & 0xff;

                    /* no stars in the status area */
                    if (x >= 240) {
                        continue;
                    }

                    plot_star(bitmap, x, y, stars[offs].color);
                }
            }
        }
    };

    public static timer_callback stars_blink_callback = new timer_callback() {
        public void handler(int i) {
            stars_blink_state++;
        }
    };

    static void start_stars_blink_timer(double ra, double rb, double c) {
        /* calculate the period using the formula given in the 555 datasheet */

        double period = 0.693 * (ra + 2.0 * rb) * c;

        stars_blink_timer = timer_pulse(TIME_IN_SEC(period), 0, stars_blink_callback);
    }

    static timer_callback stars_scroll_callback = new timer_callback() {
        public void handler(int i) {
            if (galaxian_stars_on != 0) {
                stars_scrollpos++;
            }
        }
    };

    static void start_stars_scroll_timer() {
        stars_scroll_timer = timer_pulse(TIME_IN_HZ(Machine.drv.frames_per_second), 0, stars_scroll_callback);
    }

    /**
     * *************************************************************************
     *
     * Draw the game screen in the given mame_bitmap. Do NOT call
     * osd_update_display() from this function, it will be called by the main
     * emulation engine.
     *
     **************************************************************************
     */
    public static VhUpdatePtr galaxian_vh_screenrefresh = new VhUpdatePtr() {
        public void handler(mame_bitmap bitmap, int full_refresh) {
            int x, y;
            int offs, color_mask;
            int transparency;

            color_mask = (Machine.gfx[0].color_granularity == 4) ? 7 : 3;

            /* draw the bacground */
            if (draw_background != null) {
                draw_background.handler(bitmap);
            } else {
                if (draw_stars != null) {
                    /* black base for stars */
                    fillbitmap(bitmap, Machine.pens.read(0), Machine.visible_area);
                }
            }

            /* draw the stars */
            if (draw_stars != null && galaxian_stars_on != 0) {
                draw_stars.handler(bitmap);
            }

            /* draw the character layer */
            transparency = ((draw_background != null) || (draw_stars != null)) ? TRANSPARENCY_PEN : TRANSPARENCY_NONE;

            for (x = 0; x < 32; x++) {
                int/*UINT8*/ sx;
                int[]/*UINT8*/ scroll = new int[1];
                int[] color = new int[1];

                scroll[0] = galaxian_attributesram.read(x << 1);
                color[0] = galaxian_attributesram.read((x << 1) | 1) & color_mask;

                if (modify_color != null) {
                    modify_color.handler(color);
                }

                if (modify_ypos != null) {
                    modify_ypos.handler(scroll);
                }

                sx = 8 * x;

                if (flip_screen_x[0] != 0) {
                    sx = 248 - sx;
                }

                for (y = 0; y < 32; y++) {
                    int/*UINT8*/ u8_sy;
                    int[] code = new int[1];

                    u8_sy = ((8 * y) - scroll[0]) & 0xFF;

                    if (flip_screen_y[0] != 0) {
                        u8_sy = (248 - u8_sy) & 0xFF;
                    }

                    code[0] = galaxian_videoram.read((y << 5) | x);

                    if (modify_charcode != null) {
                        modify_charcode.handler(code, x);
                    }

                    drawgfx(bitmap, Machine.gfx[0],
                            code[0], color[0],
                            flip_screen_x[0], flip_screen_y[0],
                            sx, u8_sy,
                            null, transparency, 0);
                }
            }

            /* draw the bullets */
            if (draw_bullets != null) {
                for (offs = 0; offs < galaxian_bulletsram_size[0]; offs += 4) {
                    int/*UINT8*/ u8_sx, u8_sy;

                    u8_sy = (255 - galaxian_bulletsram.read(offs + 1)) & 0xFF;
                    u8_sx = (255 - galaxian_bulletsram.read(offs + 3)) & 0xFF;

                    if (u8_sy < Machine.visible_area.min_y
                            || u8_sy > Machine.visible_area.max_y) {
                        continue;
                    }

                    if (flip_screen_y[0] != 0) {
                        u8_sy = (255 - u8_sy) & 0xFF;
                    }

                    draw_bullets.handler(bitmap, offs, u8_sx, u8_sy);
                }
            }

            /* draw the sprites */
            for (offs = galaxian_spriteram_size[0] - 4; offs >= 0; offs -= 4) {
                int /*UINT8*/ u8_sx;
                int[] u8_sy = new int[1];
                int[] flipx = new int[1];
                int[] flipy = new int[1];
                int[] code = new int[1];
                int[] color = new int[1];

                u8_sx = (galaxian_spriteram.read(offs + 3) + 1) & 0xFF;/* the existence of +1 is supported by a LOT of games */
                u8_sy[0] = galaxian_spriteram.read(offs);/* Anteater, Mariner, for example */
                flipx[0] = galaxian_spriteram.read(offs + 1) & 0x40;
                flipy[0] = galaxian_spriteram.read(offs + 1) & 0x80;
                code[0] = galaxian_spriteram.read(offs + 1) & 0x3f;
                color[0] = galaxian_spriteram.read(offs + 2) & color_mask;

                if (modify_spritecode != null) {
                    modify_spritecode.handler(code, flipx, flipy, offs);
                }

                if (modify_color != null) {
                    modify_color.handler(color);
                }

                if (modify_ypos != null) {
                    modify_ypos.handler(u8_sy);
                }

                if (flip_screen_x[0] != 0) {
                    u8_sx = (240 - u8_sx) & 0xFF;
                    flipx[0] = NOT(flipx[0]);
                }

                if (flip_screen_y[0] != 0) {
                    flipy[0] = NOT(flipy[0]);
                } else {
                    u8_sy[0] = (240 - u8_sy[0]) & 0xFF;
                }

                /* In at least Amidar Turtles, sprites #0, #1 and #2 need to be moved */
 /* down (left) one pixel to be positioned correctly. */
 /* Note that the adjustment must be done AFTER handling flipscreen, thus */
 /* proving that this is a hardware related "feature" */
                if (offs < 3 * 4) {
                    u8_sy[0] = (u8_sy[0] + 1) & 0xFF;//sy++;
                }

                drawgfx(bitmap, Machine.gfx[1],
                        code[0], color[0],
                        flipx[0], flipy[0],
                        u8_sx, u8_sy[0],
                        flip_screen_x[0] != 0 ? spritevisibleareaflipx : spritevisiblearea, TRANSPARENCY_PEN, 0);
            }
        }
    };

    public static InterruptPtr hunchbks_vh_interrupt = new InterruptPtr() {
        public int handler() {
            cpu_irq_line_vector_w(0, 0, 0x03);
            cpu_set_irq_line(0, 0, PULSE_LINE);

            return ignore_interrupt.handler();
        }
    };
}
