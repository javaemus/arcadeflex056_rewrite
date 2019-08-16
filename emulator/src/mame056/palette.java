/**
 * Ported to 0.56
 */
package mame056;

import static WIP2.common.subArrays.*;
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.arcadeflex056.video.osd_allocate_colors;
import static WIP2.arcadeflex056.video.osd_modify_pen;
import static WIP2.mame056.common.memory_region;
import static WIP2.mame056.commonH.REGION_PROMS;
import static WIP2.mame056.driverH.VIDEO_HAS_HIGHLIGHTS;
import static WIP2.mame056.driverH.VIDEO_HAS_SHADOWS;
import static WIP2.mame056.driverH.VIDEO_RGB_DIRECT;
import static WIP2.mame056.mame.Machine;
import static WIP2.mame056.paletteH.PALETTE_DEFAULT_HIGHLIGHT_FACTOR;
import static WIP2.mame056.paletteH.PALETTE_DEFAULT_SHADOW_FACTOR;
import static WIP2.mame056.usrintrf.usrintf_showmessage;

public class palette {

    public static char[] u8_game_palette;/* RGB palette as set by the driver */
    public static char[] u8_actual_palette;/* actual RGB palette after brightness adjustments */
    public static double[] brightness;

    public static int colormode;
    public static final int PALETTIZED_16BIT = 0;
    public static final int DIRECT_15BIT = 1;
    public static final int DIRECT_32BIT = 2;

    public static int total_colors;
    public static double shadow_factor, highlight_factor;
    public static int palette_initialized;

    /*TODO*///UINT32 direct_rgb_components[3];
    public static char[] palette_shadow_table;

    public static int palette_start() {
        int i;

        if ((Machine.drv.video_attributes & VIDEO_RGB_DIRECT) != 0 && Machine.drv.color_table_len != 0) {
            logerror("Error: VIDEO_RGB_DIRECT requires color_table_len to be 0.\n");
            return 1;
        }

        total_colors = Machine.drv.total_colors;
        if ((Machine.drv.video_attributes & VIDEO_HAS_SHADOWS) != 0) {
            total_colors += Machine.drv.total_colors;
        }
        if ((Machine.drv.video_attributes & VIDEO_HAS_HIGHLIGHTS) != 0) {
            total_colors += Machine.drv.total_colors;
        }
        if (total_colors > 65536) {
            logerror("Error: palette has more than 65536 colors.\n");
            return 1;
        }
        shadow_factor = PALETTE_DEFAULT_SHADOW_FACTOR;
        highlight_factor = PALETTE_DEFAULT_HIGHLIGHT_FACTOR;

        u8_game_palette = new char[3 * total_colors];
        u8_actual_palette = new char[3 * total_colors];
        brightness = new double[4 * Machine.drv.total_colors];

        if (Machine.color_depth == 15) {
            colormode = DIRECT_15BIT;
        } else if (Machine.color_depth == 32) {
            colormode = DIRECT_32BIT;
        } else {
            colormode = PALETTIZED_16BIT;
        }

        Machine.pens = new int[total_colors * 4];//malloc(total_colors * sizeof(*Machine->pens));

        if (Machine.drv.color_table_len != 0) {
            Machine.game_colortable = new char[Machine.drv.color_table_len * 2];
            Machine.remapped_colortable = new IntArray(Machine.drv.color_table_len * 4);
        } else {
            Machine.game_colortable = null;
            Machine.remapped_colortable = new IntArray(Machine.pens);/* straight 1:1 mapping from palette to colortable */
        }

        if (colormode == PALETTIZED_16BIT) {
            palette_shadow_table = new char[total_colors * 2];
            if (palette_shadow_table == null) {
                palette_stop();
                return 1;
            }
            for (i = 0; i < total_colors; i++) {
                palette_shadow_table[i] = (char) i;
                if ((Machine.drv.video_attributes & VIDEO_HAS_SHADOWS) != 0 && i < Machine.drv.total_colors) {
                    palette_shadow_table[i] += Machine.drv.total_colors;
                }
            }
        } else {
            palette_shadow_table = null;
        }

        if ((Machine.drv.color_table_len != 0 && (Machine.game_colortable == null || Machine.remapped_colortable == null)) || u8_game_palette == null || u8_actual_palette == null || brightness == null) {
            palette_stop();
            return 1;
        }

        for (i = 0; i < Machine.drv.total_colors; i++) {
            brightness[i] = 1.0;
        }

        /*TODO*///
/*TODO*///	state_save_register_UINT8("palette", 0, "colors", game_palette, total_colors*3);
/*TODO*///	state_save_register_UINT8("palette", 0, "actual_colors", actual_palette, total_colors*3);
/*TODO*///	state_save_register_double("palette", 0, "brightness", brightness, Machine.drv.total_colors);
        switch (colormode) {
            case PALETTIZED_16BIT:
                if (palette_initialized != 0)//check to be removed
                {
                    throw new UnsupportedOperationException("Unsupported");
                }
                /*TODO*///			state_save_register_func_postload(palette_reset_16_palettized);
                break;
            case DIRECT_15BIT:
                throw new UnsupportedOperationException("Unsupported");
            /*TODO*///			state_save_register_func_postload(palette_reset_15_direct);
/*TODO*///			break;
            case DIRECT_32BIT:
                throw new UnsupportedOperationException("Unsupported");
            /*TODO*///			state_save_register_func_postload(palette_reset_32_direct);
/*TODO*///			break;
        }

        return 0;
    }

    public static void palette_stop() {
        u8_game_palette = null;
        u8_actual_palette = null;
        brightness = null;
        if (Machine.game_colortable != null) {
            Machine.game_colortable = null;
            /* remapped_colortable is malloc()ed only when game_colortable is, */
 /* otherwise it just points to Machine->pens */
            Machine.remapped_colortable = null;
        }
        Machine.remapped_colortable = null;
        Machine.pens = null;
        palette_shadow_table = null;

        palette_initialized = 0;
    }

    public static int palette_init() {
        int i;
        /* We initialize the palette and colortable to some default values so that */
 /* drivers which dynamically change the palette don't need a vh_init_palette() */
 /* function (provided the default color table fits their needs). */

        for (i = 0; i < total_colors; i++) {
            u8_game_palette[3 * i + 0] = u8_actual_palette[3 * i + 0] = (char) (((i & 1) >> 0) * 0xff);
            u8_game_palette[3 * i + 1] = u8_actual_palette[3 * i + 1] = (char) (((i & 2) >> 1) * 0xff);
            u8_game_palette[3 * i + 2] = u8_actual_palette[3 * i + 2] = (char) (((i & 4) >> 2) * 0xff);
        }

        /* Preload the colortable with a default setting, following the same */
 /* order of the palette. The driver can overwrite this in */
 /* vh_init_palette() */
        for (i = 0; i < Machine.drv.color_table_len; i++) {
            Machine.game_colortable[i] = (char) (i % total_colors);
        }

        /* now the driver can modify the default values if it wants to. */
        if (Machine.drv.vh_init_palette != null) {
            if (memory_region(REGION_PROMS) != null) {
                (Machine.drv.vh_init_palette).handler(u8_game_palette, Machine.game_colortable, memory_region(REGION_PROMS));
            } else {
                (Machine.drv.vh_init_palette).handler(u8_game_palette, Machine.game_colortable, null);
            }
        }

        switch (colormode) {
            case PALETTIZED_16BIT: {
                if (osd_allocate_colors(total_colors, u8_game_palette, null) != 0) {
                    return 1;
                }

                for (i = 0; i < total_colors; i++) {
                    Machine.pens[i] = i;
                }

                /* refresh the palette to support shadows in PROM games */
                for (i = 0; i < Machine.drv.total_colors; i++) {
                    palette_set_color(i, u8_game_palette[3 * i + 0], u8_game_palette[3 * i + 1], u8_game_palette[3 * i + 2]);
                }
            }
            break;

            case DIRECT_15BIT:
                throw new UnsupportedOperationException("Unsupported");
            /*TODO*///		{
/*TODO*///			const UINT8 rgbpalette[3*3] = { 0xff,0x00,0x00, 0x00,0xff,0x00, 0x00,0x00,0xff };
/*TODO*///
/*TODO*///			if (osd_allocate_colors(3,rgbpalette,direct_rgb_components,debug_palette,debug_pens))
/*TODO*///				return 1;
/*TODO*///
/*TODO*///			for (i = 0;i < total_colors;i++)
/*TODO*///				Machine->pens[i] =
/*TODO*///						(game_palette[3*i + 0] >> 3) * (direct_rgb_components[0] / 0x1f) +
/*TODO*///						(game_palette[3*i + 1] >> 3) * (direct_rgb_components[1] / 0x1f) +
/*TODO*///						(game_palette[3*i + 2] >> 3) * (direct_rgb_components[2] / 0x1f);
/*TODO*///
/*TODO*///			break;
/*TODO*///		}
/*TODO*///
            case DIRECT_32BIT:
                throw new UnsupportedOperationException("Unsupported");
            /*TODO*///		{
/*TODO*///			const UINT8 rgbpalette[3*3] = { 0xff,0x00,0x00, 0x00,0xff,0x00, 0x00,0x00,0xff };
/*TODO*///
/*TODO*///			if (osd_allocate_colors(3,rgbpalette,direct_rgb_components,debug_palette,debug_pens))
/*TODO*///				return 1;
/*TODO*///
/*TODO*///			for (i = 0;i < total_colors;i++)
/*TODO*///				Machine->pens[i] =
/*TODO*///						game_palette[3*i + 0] * (direct_rgb_components[0] / 0xff) +
/*TODO*///						game_palette[3*i + 1] * (direct_rgb_components[1] / 0xff) +
/*TODO*///						game_palette[3*i + 2] * (direct_rgb_components[2] / 0xff);
/*TODO*///
/*TODO*///			break;
/*TODO*///		}
        }

        for (i = 0; i < Machine.drv.color_table_len; i++) {
            int color = Machine.game_colortable[i];

            /* check for invalid colors set by Machine->drv->vh_init_palette */
            if (color < total_colors) {
                Machine.remapped_colortable.write(i, Machine.pens[color]);
            } else {
                usrintf_showmessage("colortable[%d] (=%d) out of range (total_colors = %d)", i, color, total_colors);
            }
        }

        palette_initialized = 1;

        return 0;
    }

    /*TODO*///
/*TODO*///
/*TODO*///
/*TODO*///INLINE void palette_set_color_15_direct(int color,UINT8 red,UINT8 green,UINT8 blue)
/*TODO*///{
/*TODO*///	if (	actual_palette[3*color + 0] == red &&
/*TODO*///			actual_palette[3*color + 1] == green &&
/*TODO*///			actual_palette[3*color + 2] == blue)
/*TODO*///		return;
/*TODO*///	actual_palette[3*color + 0] = red;
/*TODO*///	actual_palette[3*color + 1] = green;
/*TODO*///	actual_palette[3*color + 2] = blue;
/*TODO*///	Machine->pens[color] =
/*TODO*///			(red   >> 3) * (direct_rgb_components[0] / 0x1f) +
/*TODO*///			(green >> 3) * (direct_rgb_components[1] / 0x1f) +
/*TODO*///			(blue  >> 3) * (direct_rgb_components[2] / 0x1f);
/*TODO*///}
/*TODO*///
/*TODO*///static void palette_reset_15_direct(void)
/*TODO*///{
/*TODO*///	int color;
/*TODO*///	for(color = 0; color < total_colors; color++)
/*TODO*///		Machine->pens[color] =
/*TODO*///				(game_palette[3*color + 0]>>3) * (direct_rgb_components[0] / 0x1f) +
/*TODO*///				(game_palette[3*color + 1]>>3) * (direct_rgb_components[1] / 0x1f) +
/*TODO*///				(game_palette[3*color + 2]>>3) * (direct_rgb_components[2] / 0x1f);
/*TODO*///}
/*TODO*///
/*TODO*///INLINE void palette_set_color_32_direct(int color,UINT8 red,UINT8 green,UINT8 blue)
/*TODO*///{
/*TODO*///	if (	actual_palette[3*color + 0] == red &&
/*TODO*///			actual_palette[3*color + 1] == green &&
/*TODO*///			actual_palette[3*color + 2] == blue)
/*TODO*///		return;
/*TODO*///	actual_palette[3*color + 0] = red;
/*TODO*///	actual_palette[3*color + 1] = green;
/*TODO*///	actual_palette[3*color + 2] = blue;
/*TODO*///	Machine->pens[color] =
/*TODO*///			red   * (direct_rgb_components[0] / 0xff) +
/*TODO*///			green * (direct_rgb_components[1] / 0xff) +
/*TODO*///			blue  * (direct_rgb_components[2] / 0xff);
/*TODO*///}
/*TODO*///
/*TODO*///static void palette_reset_32_direct(void)
/*TODO*///{
/*TODO*///	int color;
/*TODO*///	for(color = 0; color < total_colors; color++)
/*TODO*///		Machine->pens[color] =
/*TODO*///				game_palette[3*color + 0] * (direct_rgb_components[0] / 0xff) +
/*TODO*///				game_palette[3*color + 1] * (direct_rgb_components[1] / 0xff) +
/*TODO*///				game_palette[3*color + 2] * (direct_rgb_components[2] / 0xff);
/*TODO*///}

    public static void palette_set_color_16_palettized(int color,/*UINT8*/ int u8_red,/*UINT8*/ int u8_green,/*UINT8*/ int u8_blue) {
        int red = u8_red & 0xff;
        int green = u8_green & 0xff;
        int blue = u8_blue & 0xff;
        if (u8_actual_palette[3 * color + 0] == red
                && u8_actual_palette[3 * color + 1] == green
                && u8_actual_palette[3 * color + 2] == blue) {
            return;
        }

        u8_actual_palette[3 * color + 0] = (char) red;
        u8_actual_palette[3 * color + 1] = (char) green;
        u8_actual_palette[3 * color + 2] = (char) blue;

        if (palette_initialized != 0) {
            osd_modify_pen(Machine.pens[color], red, green, blue);
        }
    }

    /*TODO*///static void palette_reset_16_palettized(void)
/*TODO*///{
/*TODO*///	if (palette_initialized)
/*TODO*///	{
/*TODO*///		int color;
/*TODO*///		for (color=0; color<total_colors; color++)
/*TODO*///			osd_modify_pen(Machine->pens[color],
/*TODO*///						   game_palette[3*color + 0],
/*TODO*///						   game_palette[3*color + 1],
/*TODO*///						   game_palette[3*color + 2]);
/*TODO*///   }
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void adjust_shadow(UINT8 *r,UINT8 *g,UINT8 *b,double factor)
/*TODO*///{
/*TODO*///	if (factor > 1)
/*TODO*///	{
/*TODO*///		int max = *r;
/*TODO*///		if (*g > max) max = *g;
/*TODO*///		if (*b > max) max = *b;
/*TODO*///
/*TODO*///		if ((int)(max * factor + 0.5) >= 256)
/*TODO*///			factor = 255.0 / max;
/*TODO*///	}
/*TODO*///
/*TODO*///	*r = *r * factor + 0.5;
/*TODO*///	*g = *g * factor + 0.5;
/*TODO*///	*b = *b * factor + 0.5;
/*TODO*///}
    public static void palette_set_color(int color,/*UINT8*/ int u8_r,/*UINT8*/ int u8_g,/*UINT8*/ int u8_b) {
        int r = u8_r & 0xff;
        int g = u8_g & 0xff;
        int b = u8_b & 0xff;
        if (color >= total_colors) {
            logerror("error: palette_set_color() called with color %d, but only %d allocated.\n", color, total_colors);
            return;
        }

        u8_game_palette[3 * color + 0] = (char) r;
        u8_game_palette[3 * color + 1] = (char) g;
        u8_game_palette[3 * color + 2] = (char) b;

        if (color < Machine.drv.total_colors && brightness[color] != 1.0) {
            r = (int) (r * brightness[color] + 0.5) & 0xFF;
            g = (int) (g * brightness[color] + 0.5) & 0xFF;
            b = (int) (b * brightness[color] + 0.5) & 0xFF;
        }

        switch (colormode) {
            case PALETTIZED_16BIT:
                palette_set_color_16_palettized(color, r, g, b);
                break;
            case DIRECT_15BIT:
                throw new UnsupportedOperationException("Unsupported");
            /*TODO*///			palette_set_color_15_direct(color,r,g,b);
/*TODO*///			break;
            case DIRECT_32BIT:
                throw new UnsupportedOperationException("Unsupported");
            /*TODO*///			palette_set_color_32_direct(color,r,g,b);
/*TODO*///			break;
        }

        if (color < Machine.drv.total_colors) {
            /* automatically create darker shade for shadow handling */
            if ((Machine.drv.video_attributes & VIDEO_HAS_SHADOWS) != 0) {
                throw new UnsupportedOperationException("Unsupported");
                /*TODO*///			UINT8 nr=r,ng=g,nb=b;
/*TODO*///
/*TODO*///			adjust_shadow(&nr,&ng,&nb,shadow_factor);
/*TODO*///
/*TODO*///			color += Machine->drv->total_colors;	/* carry this change over to highlight handling */
/*TODO*///			palette_set_color(color,nr,ng,nb);
            }

            /* automatically create brighter shade for highlight handling */
            if ((Machine.drv.video_attributes & VIDEO_HAS_HIGHLIGHTS) != 0)
            {
                throw new UnsupportedOperationException("Unsupported");
                /*TODO*///			UINT8 nr=r,ng=g,nb=b;
/*TODO*///
/*TODO*///			adjust_shadow(&nr,&ng,&nb,highlight_factor);
/*TODO*///
/*TODO*///			color += Machine->drv->total_colors;
/*TODO*///			palette_set_color(color,nr,ng,nb);
            }
        }
    }
    /*TODO*///
/*TODO*///void palette_get_color(int color,UINT8 *r,UINT8 *g,UINT8 *b)
/*TODO*///{
/*TODO*///	*r = game_palette[3*color + 0];
/*TODO*///	*g = game_palette[3*color + 1];
/*TODO*///	*b = game_palette[3*color + 2];
/*TODO*///}
/*TODO*///
/*TODO*///void palette_set_brightness(int color,double bright)
/*TODO*///{
/*TODO*///	if (brightness[color] != bright)
/*TODO*///	{
/*TODO*///		brightness[color] = bright;
/*TODO*///
/*TODO*///		palette_set_color(color,game_palette[3*color + 0],game_palette[3*color + 1],game_palette[3*color + 2]);
/*TODO*///	}
/*TODO*///}
/*TODO*///
/*TODO*///void palette_set_shadow_factor(double factor)
/*TODO*///{
/*TODO*///	if (shadow_factor != factor)
/*TODO*///	{
/*TODO*///		int i;
/*TODO*///
/*TODO*///		shadow_factor = factor;
/*TODO*///
/*TODO*///		if (palette_initialized)
/*TODO*///		{
/*TODO*///			for (i = 0;i < Machine->drv->total_colors;i++)
/*TODO*///				palette_set_color(i,game_palette[3*i + 0],game_palette[3*i + 1],game_palette[3*i + 2]);
/*TODO*///		}
/*TODO*///	}
/*TODO*///}
/*TODO*///
/*TODO*///void palette_set_highlight_factor(double factor)
/*TODO*///{
/*TODO*///	if (highlight_factor != factor)
/*TODO*///	{
/*TODO*///		int i;
/*TODO*///
/*TODO*///		highlight_factor = factor;
/*TODO*///
/*TODO*///		for (i = 0;i < Machine->drv->total_colors;i++)
/*TODO*///			palette_set_color(i,game_palette[3*i + 0],game_palette[3*i + 1],game_palette[3*i + 2]);
/*TODO*///	}
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///
/*TODO*////******************************************************************************
/*TODO*///
/*TODO*/// Commonly used palette RAM handling functions
/*TODO*///
/*TODO*///******************************************************************************/
/*TODO*///
/*TODO*///data8_t *paletteram;
/*TODO*///data8_t *paletteram_2;	/* use when palette RAM is split in two parts */
/*TODO*///data16_t *paletteram16;
/*TODO*///data16_t *paletteram16_2;
/*TODO*///data32_t *paletteram32;
/*TODO*///
/*TODO*///READ_HANDLER( paletteram_r )
/*TODO*///{
/*TODO*///	return paletteram[offset];
/*TODO*///}
/*TODO*///
/*TODO*///READ_HANDLER( paletteram_2_r )
/*TODO*///{
/*TODO*///	return paletteram_2[offset];
/*TODO*///}
/*TODO*///
/*TODO*///READ16_HANDLER( paletteram16_word_r )
/*TODO*///{
/*TODO*///	return paletteram16[offset];
/*TODO*///}
/*TODO*///
/*TODO*///READ16_HANDLER( paletteram16_2_word_r )
/*TODO*///{
/*TODO*///	return paletteram16_2[offset];
/*TODO*///}
/*TODO*///
/*TODO*///READ32_HANDLER( paletteram32_r )
/*TODO*///{
/*TODO*///	return paletteram32[offset];
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_RRRGGGBB_w )
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///	int bit0,bit1,bit2;
/*TODO*///
/*TODO*///
/*TODO*///	paletteram[offset] = data;
/*TODO*///
/*TODO*///	/* red component */
/*TODO*///	bit0 = (data >> 5) & 0x01;
/*TODO*///	bit1 = (data >> 6) & 0x01;
/*TODO*///	bit2 = (data >> 7) & 0x01;
/*TODO*///	r = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///	/* green component */
/*TODO*///	bit0 = (data >> 2) & 0x01;
/*TODO*///	bit1 = (data >> 3) & 0x01;
/*TODO*///	bit2 = (data >> 4) & 0x01;
/*TODO*///	g = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///	/* blue component */
/*TODO*///	bit0 = 0;
/*TODO*///	bit1 = (data >> 0) & 0x01;
/*TODO*///	bit2 = (data >> 1) & 0x01;
/*TODO*///	b = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///
/*TODO*///	palette_set_color(offset,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_BBBGGGRR_w )
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///	int bit0,bit1,bit2;
/*TODO*///
/*TODO*///	paletteram[offset] = data;
/*TODO*///
/*TODO*///	/* blue component */
/*TODO*///	bit0 = (data >> 5) & 0x01;
/*TODO*///	bit1 = (data >> 6) & 0x01;
/*TODO*///	bit2 = (data >> 7) & 0x01;
/*TODO*///	b = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///	/* green component */
/*TODO*///	bit0 = (data >> 2) & 0x01;
/*TODO*///	bit1 = (data >> 3) & 0x01;
/*TODO*///	bit2 = (data >> 4) & 0x01;
/*TODO*///	g = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///	/* blue component */
/*TODO*///	bit0 = (data >> 0) & 0x01;
/*TODO*///	bit1 = (data >> 1) & 0x01;
/*TODO*///	r = 0x55 * bit0 + 0xaa * bit1;
/*TODO*///
/*TODO*///	palette_set_color(offset,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_BBGGGRRR_w )
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///	int bit0,bit1,bit2;
/*TODO*///
/*TODO*///
/*TODO*///	paletteram[offset] = data;
/*TODO*///
/*TODO*///	/* red component */
/*TODO*///	bit0 = (data >> 0) & 0x01;
/*TODO*///	bit1 = (data >> 1) & 0x01;
/*TODO*///	bit2 = (data >> 2) & 0x01;
/*TODO*///	r = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///	/* green component */
/*TODO*///	bit0 = (data >> 3) & 0x01;
/*TODO*///	bit1 = (data >> 4) & 0x01;
/*TODO*///	bit2 = (data >> 5) & 0x01;
/*TODO*///	g = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///	/* blue component */
/*TODO*///	bit0 = 0;
/*TODO*///	bit1 = (data >> 6) & 0x01;
/*TODO*///	bit2 = (data >> 7) & 0x01;
/*TODO*///	b = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
/*TODO*///
/*TODO*///	palette_set_color(offset,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_IIBBGGRR_w )
/*TODO*///{
/*TODO*///	int r,g,b,i;
/*TODO*///
/*TODO*///
/*TODO*///	paletteram[offset] = data;
/*TODO*///
/*TODO*///	i = (data >> 6) & 0x03;
/*TODO*///	/* red component */
/*TODO*///	r = (data << 2) & 0x0c;
/*TODO*///	if (r) r |= i;
/*TODO*///	r *= 0x11;
/*TODO*///	/* green component */
/*TODO*///	g = (data >> 0) & 0x0c;
/*TODO*///	if (g) g |= i;
/*TODO*///	g *= 0x11;
/*TODO*///	/* blue component */
/*TODO*///	b = (data >> 2) & 0x0c;
/*TODO*///	if (b) b |= i;
/*TODO*///	b *= 0x11;
/*TODO*///
/*TODO*///	palette_set_color(offset,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_BBGGRRII_w )
/*TODO*///{
/*TODO*///	int r,g,b,i;
/*TODO*///
/*TODO*///
/*TODO*///	paletteram[offset] = data;
/*TODO*///
/*TODO*///	i = (data >> 0) & 0x03;
/*TODO*///	/* red component */
/*TODO*///	r = (((data >> 0) & 0x0c) | i) * 0x11;
/*TODO*///	/* green component */
/*TODO*///	g = (((data >> 2) & 0x0c) | i) * 0x11;
/*TODO*///	/* blue component */
/*TODO*///	b = (((data >> 4) & 0x0c) | i) * 0x11;
/*TODO*///
/*TODO*///	palette_set_color(offset,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_xxxxBBBBGGGGRRRR(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >> 0) & 0x0f;
/*TODO*///	g = (data >> 4) & 0x0f;
/*TODO*///	b = (data >> 8) & 0x0f;
/*TODO*///
/*TODO*///	r = (r << 4) | r;
/*TODO*///	g = (g << 4) | g;
/*TODO*///	b = (b << 4) | b;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBGGGGRRRR_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxBBBBGGGGRRRR(offset / 2,paletteram[offset & ~1] | (paletteram[offset | 1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBGGGGRRRR_swap_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxBBBBGGGGRRRR(offset / 2,paletteram[offset | 1] | (paletteram[offset & ~1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBGGGGRRRR_split1_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxBBBBGGGGRRRR(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBGGGGRRRR_split2_w )
/*TODO*///{
/*TODO*///	paletteram_2[offset] = data;
/*TODO*///	changecolor_xxxxBBBBGGGGRRRR(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_xxxxBBBBGGGGRRRR_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_xxxxBBBBGGGGRRRR(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_xxxxBBBBRRRRGGGG(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >> 4) & 0x0f;
/*TODO*///	g = (data >> 0) & 0x0f;
/*TODO*///	b = (data >> 8) & 0x0f;
/*TODO*///
/*TODO*///	r = (r << 4) | r;
/*TODO*///	g = (g << 4) | g;
/*TODO*///	b = (b << 4) | b;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBRRRRGGGG_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxBBBBRRRRGGGG(offset / 2,paletteram[offset & ~1] | (paletteram[offset | 1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBRRRRGGGG_swap_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxBBBBRRRRGGGG(offset / 2,paletteram[offset | 1] | (paletteram[offset & ~1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBRRRRGGGG_split1_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxBBBBRRRRGGGG(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxBBBBRRRRGGGG_split2_w )
/*TODO*///{
/*TODO*///	paletteram_2[offset] = data;
/*TODO*///	changecolor_xxxxBBBBRRRRGGGG(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_xxxxRRRRBBBBGGGG(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >> 8) & 0x0f;
/*TODO*///	g = (data >> 0) & 0x0f;
/*TODO*///	b = (data >> 4) & 0x0f;
/*TODO*///
/*TODO*///	r = (r << 4) | r;
/*TODO*///	g = (g << 4) | g;
/*TODO*///	b = (b << 4) | b;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxRRRRBBBBGGGG_split1_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxRRRRBBBBGGGG(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxRRRRBBBBGGGG_split2_w )
/*TODO*///{
/*TODO*///	paletteram_2[offset] = data;
/*TODO*///	changecolor_xxxxRRRRBBBBGGGG(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_xxxxRRRRGGGGBBBB(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >> 8) & 0x0f;
/*TODO*///	g = (data >> 4) & 0x0f;
/*TODO*///	b = (data >> 0) & 0x0f;
/*TODO*///
/*TODO*///	r = (r << 4) | r;
/*TODO*///	g = (g << 4) | g;
/*TODO*///	b = (b << 4) | b;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxRRRRGGGGBBBB_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxRRRRGGGGBBBB(offset / 2,paletteram[offset & ~1] | (paletteram[offset | 1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xxxxRRRRGGGGBBBB_swap_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xxxxRRRRGGGGBBBB(offset / 2,paletteram[offset | 1] | (paletteram[offset & ~1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_xxxxRRRRGGGGBBBB_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_xxxxRRRRGGGGBBBB(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_RRRRGGGGBBBBxxxx(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >> 12) & 0x0f;
/*TODO*///	g = (data >>  8) & 0x0f;
/*TODO*///	b = (data >>  4) & 0x0f;
/*TODO*///
/*TODO*///	r = (r << 4) | r;
/*TODO*///	g = (g << 4) | g;
/*TODO*///	b = (b << 4) | b;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_RRRRGGGGBBBBxxxx_swap_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_RRRRGGGGBBBBxxxx(offset / 2,paletteram[offset | 1] | (paletteram[offset & ~1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_RRRRGGGGBBBBxxxx_split1_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_RRRRGGGGBBBBxxxx(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_RRRRGGGGBBBBxxxx_split2_w )
/*TODO*///{
/*TODO*///	paletteram_2[offset] = data;
/*TODO*///	changecolor_RRRRGGGGBBBBxxxx(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_RRRRGGGGBBBBxxxx_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_RRRRGGGGBBBBxxxx(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_BBBBGGGGRRRRxxxx(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >>  4) & 0x0f;
/*TODO*///	g = (data >>  8) & 0x0f;
/*TODO*///	b = (data >> 12) & 0x0f;
/*TODO*///
/*TODO*///	r = (r << 4) | r;
/*TODO*///	g = (g << 4) | g;
/*TODO*///	b = (b << 4) | b;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_BBBBGGGGRRRRxxxx_swap_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_BBBBGGGGRRRRxxxx(offset / 2,paletteram[offset | 1] | (paletteram[offset & ~1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_BBBBGGGGRRRRxxxx_split1_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_BBBBGGGGRRRRxxxx(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_BBBBGGGGRRRRxxxx_split2_w )
/*TODO*///{
/*TODO*///	paletteram_2[offset] = data;
/*TODO*///	changecolor_BBBBGGGGRRRRxxxx(offset,paletteram[offset] | (paletteram_2[offset] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_BBBBGGGGRRRRxxxx_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_BBBBGGGGRRRRxxxx(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_xBBBBBGGGGGRRRRR(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >>  0) & 0x1f;
/*TODO*///	g = (data >>  5) & 0x1f;
/*TODO*///	b = (data >> 10) & 0x1f;
/*TODO*///
/*TODO*///	r = (r << 3) | (r >> 2);
/*TODO*///	g = (g << 3) | (g >> 2);
/*TODO*///	b = (b << 3) | (b >> 2);
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xBBBBBGGGGGRRRRR_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xBBBBBGGGGGRRRRR(offset / 2,paletteram[offset & ~1] | (paletteram[offset | 1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xBBBBBGGGGGRRRRR_swap_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xBBBBBGGGGGRRRRR(offset / 2,paletteram[offset | 1] | (paletteram[offset & ~1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_xBBBBBGGGGGRRRRR_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_xBBBBBGGGGGRRRRR(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_xRRRRRGGGGGBBBBB(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >> 10) & 0x1f;
/*TODO*///	g = (data >>  5) & 0x1f;
/*TODO*///	b = (data >>  0) & 0x1f;
/*TODO*///
/*TODO*///	r = (r << 3) | (r >> 2);
/*TODO*///	g = (g << 3) | (g >> 2);
/*TODO*///	b = (b << 3) | (b >> 2);
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_xRRRRRGGGGGBBBBB_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_xRRRRRGGGGGBBBBB(offset / 2,paletteram[offset & ~1] | (paletteram[offset | 1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_xRRRRRGGGGGBBBBB_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_xRRRRRGGGGGBBBBB(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_xGGGGGRRRRRBBBBB(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >>  5) & 0x1f;
/*TODO*///	g = (data >> 10) & 0x1f;
/*TODO*///	b = (data >>  0) & 0x1f;
/*TODO*///
/*TODO*///	r = (r << 3) | (r >> 2);
/*TODO*///	g = (g << 3) | (g >> 2);
/*TODO*///	b = (b << 3) | (b >> 2);
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_xGGGGGRRRRRBBBBB_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_xGGGGGRRRRRBBBBB(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_RRRRRGGGGGBBBBBx(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	r = (data >> 11) & 0x1f;
/*TODO*///	g = (data >>  6) & 0x1f;
/*TODO*///	b = (data >>  1) & 0x1f;
/*TODO*///
/*TODO*///	r = (r << 3) | (r >> 2);
/*TODO*///	g = (g << 3) | (g >> 2);
/*TODO*///	b = (b << 3) | (b >> 2);
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE_HANDLER( paletteram_RRRRRGGGGGBBBBBx_w )
/*TODO*///{
/*TODO*///	paletteram[offset] = data;
/*TODO*///	changecolor_RRRRRGGGGGBBBBBx(offset / 2,paletteram[offset & ~1] | (paletteram[offset | 1] << 8));
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_RRRRRGGGGGBBBBBx_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_RRRRRGGGGGBBBBBx(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_IIIIRRRRGGGGBBBB(int color,int data)
/*TODO*///{
/*TODO*///	int i,r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	static const int ztable[16] =
/*TODO*///		{ 0x0, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf, 0x10, 0x11 };
/*TODO*///
/*TODO*///	i = ztable[(data >> 12) & 15];
/*TODO*///	r = ((data >> 8) & 15) * i;
/*TODO*///	g = ((data >> 4) & 15) * i;
/*TODO*///	b = ((data >> 0) & 15) * i;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///
/*TODO*///	if (!(Machine->drv->video_attributes & VIDEO_NEEDS_6BITS_PER_GUN))
/*TODO*///		usrintf_showmessage("driver should use VIDEO_NEEDS_6BITS_PER_GUN flag");
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_IIIIRRRRGGGGBBBB_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_IIIIRRRRGGGGBBBB(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_RRRRGGGGBBBBIIII(int color,int data)
/*TODO*///{
/*TODO*///	int i,r,g,b;
/*TODO*///
/*TODO*///
/*TODO*///	static const int ztable[16] =
/*TODO*///		{ 0x0, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf, 0x10, 0x11 };
/*TODO*///
/*TODO*///	i = ztable[(data >> 0) & 15];
/*TODO*///	r = ((data >> 12) & 15) * i;
/*TODO*///	g = ((data >>  8) & 15) * i;
/*TODO*///	b = ((data >>  4) & 15) * i;
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///
/*TODO*///	if (!(Machine->drv->video_attributes & VIDEO_NEEDS_6BITS_PER_GUN))
/*TODO*///		usrintf_showmessage("driver should use VIDEO_NEEDS_6BITS_PER_GUN flag");
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_RRRRGGGGBBBBIIII_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_RRRRGGGGBBBBIIII(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_xrgb_word_w )
/*TODO*///{
/*TODO*///	int r, g, b;
/*TODO*///	data16_t data0, data1;
/*TODO*///
/*TODO*///	COMBINE_DATA(paletteram16 + offset);
/*TODO*///
/*TODO*///	offset &= ~1;
/*TODO*///
/*TODO*///	data0 = paletteram16[offset];
/*TODO*///	data1 = paletteram16[offset + 1];
/*TODO*///
/*TODO*///	r = data0 & 0xff;
/*TODO*///	g = data1 >> 8;
/*TODO*///	b = data1 & 0xff;
/*TODO*///
/*TODO*///	palette_set_color(offset>>1, r, g, b);
/*TODO*///
/*TODO*///	if (!(Machine->drv->video_attributes & VIDEO_NEEDS_6BITS_PER_GUN))
/*TODO*///		usrintf_showmessage("driver should use VIDEO_NEEDS_6BITS_PER_GUN flag");
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///INLINE void changecolor_RRRRGGGGBBBBRGBx(int color,int data)
/*TODO*///{
/*TODO*///	int r,g,b;
/*TODO*///
/*TODO*///	r = ((data >> 11) & 0x1e) | ((data>>3) & 0x01);
/*TODO*///	g = ((data >>  7) & 0x1e) | ((data>>2) & 0x01);
/*TODO*///	b = ((data >>  3) & 0x1e) | ((data>>1) & 0x01);
/*TODO*///	r = (r<<3) | (r>>2);
/*TODO*///	g = (g<<3) | (g>>2);
/*TODO*///	b = (b<<3) | (b>>2);
/*TODO*///
/*TODO*///	palette_set_color(color,r,g,b);
/*TODO*///}
/*TODO*///
/*TODO*///WRITE16_HANDLER( paletteram16_RRRRGGGGBBBBRGBx_word_w )
/*TODO*///{
/*TODO*///	COMBINE_DATA(&paletteram16[offset]);
/*TODO*///	changecolor_RRRRGGGGBBBBRGBx(offset,paletteram16[offset]);
/*TODO*///}
/*TODO*///
/*TODO*///
/*TODO*///
/*TODO*////******************************************************************************
/*TODO*///
/*TODO*/// Commonly used color PROM handling functions
/*TODO*///
/*TODO*///******************************************************************************/
/*TODO*///
/*TODO*////***************************************************************************
/*TODO*///
/*TODO*///  This assumes the commonly used resistor values:
/*TODO*///
/*TODO*///  bit 3 -- 220 ohm resistor  -- RED/GREEN/BLUE
/*TODO*///        -- 470 ohm resistor  -- RED/GREEN/BLUE
/*TODO*///        -- 1  kohm resistor  -- RED/GREEN/BLUE
/*TODO*///  bit 0 -- 2.2kohm resistor  -- RED/GREEN/BLUE
/*TODO*///
/*TODO*///***************************************************************************/
/*TODO*///void palette_RRRR_GGGG_BBBB_convert_prom(unsigned char *obsolete,unsigned short *colortable,const unsigned char *color_prom)
/*TODO*///{
/*TODO*///	int i;
/*TODO*///
/*TODO*///
/*TODO*///	for (i = 0;i < Machine->drv->total_colors;i++)
/*TODO*///	{
/*TODO*///		int bit0,bit1,bit2,bit3,r,g,b;
/*TODO*///
/*TODO*///		/* red component */
/*TODO*///		bit0 = (color_prom[i] >> 0) & 0x01;
/*TODO*///		bit1 = (color_prom[i] >> 1) & 0x01;
/*TODO*///		bit2 = (color_prom[i] >> 2) & 0x01;
/*TODO*///		bit3 = (color_prom[i] >> 3) & 0x01;
/*TODO*///		r = 0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3;
/*TODO*///		/* green component */
/*TODO*///		bit0 = (color_prom[i + Machine->drv->total_colors] >> 0) & 0x01;
/*TODO*///		bit1 = (color_prom[i + Machine->drv->total_colors] >> 1) & 0x01;
/*TODO*///		bit2 = (color_prom[i + Machine->drv->total_colors] >> 2) & 0x01;
/*TODO*///		bit3 = (color_prom[i + Machine->drv->total_colors] >> 3) & 0x01;
/*TODO*///		g = 0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3;
/*TODO*///		/* blue component */
/*TODO*///		bit0 = (color_prom[i + 2*Machine->drv->total_colors] >> 0) & 0x01;
/*TODO*///		bit1 = (color_prom[i + 2*Machine->drv->total_colors] >> 1) & 0x01;
/*TODO*///		bit2 = (color_prom[i + 2*Machine->drv->total_colors] >> 2) & 0x01;
/*TODO*///		bit3 = (color_prom[i + 2*Machine->drv->total_colors] >> 3) & 0x01;
/*TODO*///		b = 0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3;
/*TODO*///
/*TODO*///		palette_set_color(i,r,g,b);
/*TODO*///	}
/*TODO*///}
/*TODO*///    
}
