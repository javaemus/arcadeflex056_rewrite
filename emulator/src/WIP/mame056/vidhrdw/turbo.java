/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */
package WIP.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;

import static WIP2.common.ptr.*;

import static WIP2.mame056.common.*;

import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import WIP2.common.subArrays.IntArray;
import static WIP2.mame056.inptport.readinputport;
import static WIP.mame056.machine.turbo.*;
import WIP2.common.subArrays.UShortArray;

public class turbo {

    /* constants */
    public static final int VIEW_WIDTH = (32 * 8);
    public static final int VIEW_HEIGHT = (28 * 8);

    /* external definitions */
    public static UBytePtr turbo_sprite_position = new UBytePtr();
    public static int u8_turbo_collision;

    /* internal data */
    static UBytePtr sprite_gfxdata;
    static UBytePtr sprite_priority;
    static UBytePtr road_gfxdata;
    static UBytePtr road_palette;
    static UBytePtr road_enable_collide;
    static UBytePtr back_gfxdata;
    static UBytePtr back_palette;
    static UBytePtr overall_priority;
    static UBytePtr collision_map;

    /* sprite tracking */
    static class sprite_params_data {

        IntArray/*UINT32*/ base;
        int offset, rowbytes;
        int yscale, miny, maxy;
        int xscale, xoffs;
    };

    static sprite_params_data[] sprite_params = new sprite_params_data[16];
    static /*UINT32*/ int[] sprite_expanded_data;

    /* misc other stuff */
    static char[] back_expanded_data;
    static char[] road_expanded_palette;
    static int drew_frame;

    /**
     * *************************************************************************
     *
     * Convert the color PROMs into a more useable format.
     *
     **************************************************************************
     */
    public static VhConvertColorPromPtr turbo_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;
            for (i = 0; i < 512; i++, color_prom.offset++) {
                int bit0, bit1, bit2;

                /* bits 4,5,6 of the index are inverted before being used as addresses */
 /* to save ourselves lots of trouble, we will undo the inversion when */
 /* generating the palette */
                int adjusted_index = i ^ 0x70;

                /* red component */
                bit0 = (color_prom.read() >> 0) & 1;
                bit1 = (color_prom.read() >> 1) & 1;
                bit2 = (color_prom.read() >> 2) & 1;
                palette[adjusted_index * 3 + 0] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);

                /* green component */
                bit0 = (color_prom.read() >> 3) & 1;
                bit1 = (color_prom.read() >> 4) & 1;
                bit2 = (color_prom.read() >> 5) & 1;
                palette[adjusted_index * 3 + 1] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);

                /* blue component */
                bit0 = 0;
                bit1 = (color_prom.read() >> 6) & 1;
                bit2 = (color_prom.read() >> 7) & 1;
                palette[adjusted_index * 3 + 2] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
            }

            /* LED segments colors: black and red */
            palette[512 * 3 + 0] = 0x00;
            palette[512 * 3 + 1] = 0x00;
            palette[512 * 3 + 2] = 0x00;
            palette[513 * 3 + 0] = 0xff;
            palette[513 * 3 + 1] = 0x00;
            palette[513 * 3 + 2] = 0x00;
            /* Tachometer colors: Led colors + yellow and green */
            palette[514 * 3 + 0] = 0x00;
            palette[514 * 3 + 1] = 0x00;
            palette[514 * 3 + 2] = 0x00;
            palette[515 * 3 + 0] = 0xff;
            palette[515 * 3 + 1] = 0xff;
            palette[515 * 3 + 2] = 0x00;
            palette[516 * 3 + 0] = 0x00;
            palette[516 * 3 + 1] = 0x00;
            palette[516 * 3 + 2] = 0x00;
            palette[517 * 3 + 0] = 0x00;
            palette[517 * 3 + 1] = 0xff;
            palette[517 * 3 + 2] = 0x00;
        }
    };

    /**
     * *************************************************************************
     *
     * Video startup/shutdown
     *
     **************************************************************************
     */
    public static VhStartPtr turbo_vh_start = new VhStartPtr() {
        public int handler() {
            int i, j, sprite_length, sprite_bank_size, back_length;
            int[]/*UINT32*/ sprite_expand = new int[16];
            //UINT32 *dst;
            //UINT16 *bdst;
            UBytePtr src;

            /* allocate the expanded sprite data */
            sprite_length = memory_region_length(REGION_GFX1);
            sprite_bank_size = sprite_length / 8;
            sprite_expanded_data = new int[sprite_length * 2 /* * sizeof(UINT32)*/];

            /* allocate the expanded background data */
            back_length = memory_region_length(REGION_GFX3);
            back_expanded_data = new char[back_length];

            /* allocate the expanded road palette */
            road_expanded_palette = new char[0x40 * 2];

            /* determine ROM/PROM addresses */
            sprite_gfxdata = memory_region(REGION_GFX1);
            sprite_priority = new UBytePtr(memory_region(REGION_PROMS), 0x0200);

            road_gfxdata = memory_region(REGION_GFX2);
            road_palette = new UBytePtr(memory_region(REGION_PROMS), 0x0b00);
            road_enable_collide = new UBytePtr(memory_region(REGION_PROMS), 0x0b40);

            back_gfxdata = memory_region(REGION_GFX3);
            back_palette = new UBytePtr(memory_region(REGION_PROMS), 0x0a00);

            overall_priority = new UBytePtr(memory_region(REGION_PROMS), 0x0600);
            collision_map = new UBytePtr(memory_region(REGION_PROMS), 0x0b60);

            /* compute the sprite expansion array */
            for (i = 0; i < 16; i++) {
                /*UINT32*/
                int value = 0;
                if ((i & 1) != 0) {
                    value |= 0x00000001;
                }
                if ((i & 2) != 0) {
                    value |= 0x00000100;
                }
                if ((i & 4) != 0) {
                    value |= 0x00010000;
                }
                if ((i & 8) != 0) {
                    value |= 0x01000000;
                }

                /* special value for the end-of-row */
                if ((i & 0x0c) == 0x04) {
                    value = 0x12345678;
                }

                sprite_expand[i] = value;
            }

            /* expand the sprite ROMs */
            src = new UBytePtr(sprite_gfxdata);
            //dst = sprite_expanded_data;
            for (i = 0; i < 8; i++) {
                int di = 0;
                /* expand this bank */
                for (j = 0; j < sprite_bank_size; j++) {
                    sprite_expanded_data[di++] = sprite_expand[src.read() >> 4];
                    sprite_expanded_data[di++] = sprite_expand[src.readinc() & 15];
                }

                /* shift for the next bank */
                for (j = 0; j < 16; j++) {
                    if (sprite_expand[j] != 0x12345678) {
                        sprite_expand[j] <<= 1;
                    }
                }
            }

            /* expand the background ROMs */
            src = new UBytePtr(back_gfxdata);
            //bdst = back_expanded_data;
            int bdi = 0;
            for (i = 0; i < back_length / 2; i++, src.offset++) {
                int bits1 = src.read(0);
                int bits2 = src.read(back_length / 2);
                int newbits = 0;

                for (j = 0; j < 8; j++) {
                    newbits |= ((bits1 >> (j ^ 7)) & 1) << (j * 2);
                    newbits |= ((bits2 >> (j ^ 7)) & 1) << (j * 2 + 1);
                }
                back_expanded_data[bdi++] = (char) newbits;
            }

            /* expand the road palette */
            src = new UBytePtr(road_palette);
            //bdst = road_expanded_palette;
            bdi = 0;
            for (i = 0; i < 0x20; i++, src.offset++) {
                road_expanded_palette[bdi++] = (char) (src.read(0) | (src.read(0x20) << 8));
            }

            /* other stuff */
            drew_frame = 0;

            /* return success */
            return 0;
        }
    };

    public static VhStopPtr turbo_vh_stop = new VhStopPtr() {
        public void handler() {
            sprite_expanded_data = null;
            back_expanded_data = null;
            road_expanded_palette = null;
        }
    };

    /**
     * *************************************************************************
     *
     * Sprite information
     *
     **************************************************************************
     */
    static void update_sprite_info() {
        /* first loop over all sprites and update those whose scanlines intersect ours */
        for (int i = 0; i < 16; i++) {
            UBytePtr sprite_base = new UBytePtr(spriteram, 16 * i);

            /* snarf all the data */
            sprite_params[i] = new sprite_params_data();
            sprite_params[i].base = new IntArray(sprite_expanded_data, (i & 7) * 0x8000);
            sprite_params[i].offset = (sprite_base.read(6) + 256 * sprite_base.read(7)) & 0x7fff;
            sprite_params[i].rowbytes = (short) (sprite_base.read(4) + 256 * sprite_base.read(5));
            sprite_params[i].miny = sprite_base.read(0);
            sprite_params[i].maxy = sprite_base.read(1);
            sprite_params[i].xscale = ((5 * 256 - 4 * sprite_base.read(2)) << 16) / (5 * 256);
            sprite_params[i].yscale = (4 << 16) / (sprite_base.read(3) + 4);
            sprite_params[i].xoffs = -1;
        }

        /* now find the X positions */
        for (int i = 0; i < 0x200; i++) {
            int value = turbo_sprite_position.read(i);
            if (value != 0) {
                int base = (i & 0x100) >> 5;
                int which;
                for (which = 0; which < 8; which++) {
                    if ((value & (1 << which)) != 0) {
                        sprite_params[base + which].xoffs = i & 0xff;
                    }
                }
            }
        }
    }

    /**
     * *************************************************************************
     *
     * Internal draw routines
     *
     **************************************************************************
     */
    static void draw_one_sprite(sprite_params_data data, /*UINT32*/ IntArray dest, int xclip, int scanline) {
        int xstep = data.xscale;
        int xoffs = data.xoffs;
        int xcurr, offset;
        IntArray/*UINT32 */ src;

        /* xoffs of -1 means don't draw */
        if (xoffs == -1) {
            return;
        }

        /* clip to the road */
        xcurr = 0;
        if (xoffs < xclip) {
            /* the pixel clock starts on xoffs regardless of clipping; take this into account */
            xcurr = ((xclip - xoffs) * xstep) & 0xffff;
            xoffs = xclip;
        }

        /* compute the current data offset */
        scanline = ((scanline - data.miny) * data.yscale) >> 16;
        offset = data.offset + (scanline + 1) * data.rowbytes;

        /* determine the bitmap location */
        src = new IntArray(data.base, (int) (offset & 0x7fff));

        /* loop over columns */
        while (xoffs < VIEW_WIDTH) {
            int/*UINT32*/ srcval = src.read(xcurr >> 16);

            /* stop on the end-of-row signal */
            if (srcval == 0x12345678) {
                break;
            }
            dest.write(xoffs++, dest.read() | srcval);
            xcurr += xstep;
        }
    }

    static void draw_road_sprites(IntArray dest, int scanline) {

        sprite_params_data[] param_list
                = {
                    sprite_params[0], sprite_params[8],
                    sprite_params[1], sprite_params[9],
                    sprite_params[2], sprite_params[10]
                };
        int i;

        /* loop over the road sprites */
        for (i = 0; i < 6; i++) {
            sprite_params_data data = param_list[i];

            /* if the sprite intersects this scanline, draw it */
            if (scanline >= data.miny && scanline < data.maxy) {
                draw_one_sprite(data, dest, 0, scanline);
            }
        }
    }

    static void draw_offroad_sprites(/*UINT32 */IntArray dest, int road_column, int scanline) {
        sprite_params_data[] param_list
                = {
                    sprite_params[3], sprite_params[11],
                    sprite_params[4], sprite_params[12],
                    sprite_params[5], sprite_params[13],
                    sprite_params[6], sprite_params[14],
                    sprite_params[7], sprite_params[15]
                };

        int i;

        /* loop over the offroad sprites */
        for (i = 0; i < 10; i++) {
            sprite_params_data data = param_list[i];

            /* if the sprite intersects this scanline, draw it */
            if (scanline >= data.miny && scanline < data.maxy) {
                draw_one_sprite(data, dest, road_column, scanline);
            }
        }
    }

    static void draw_scores(mame_bitmap bitmap) {
        rectangle clip;
        int offs, x, y;

        /* current score */
        offs = 31;
        for (y = 0; y < 5; y++, offs--) {
            drawgfx(bitmap, Machine.gfx[0],
                    turbo_segment_data[offs],
                    0,
                    0, 0,
                    14 * 8, (2 + y) * 8,
                    Machine.visible_area, TRANSPARENCY_NONE, 0);
        }

        /* high scores */
        for (x = 0; x < 5; x++) {
            offs = 6 + x * 5;
            for (y = 0; y < 5; y++, offs--) {
                drawgfx(bitmap, Machine.gfx[0],
                        turbo_segment_data[offs],
                        0,
                        0, 0,
                        (20 + 2 * x) * 8, (2 + y) * 8,
                        Machine.visible_area, TRANSPARENCY_NONE, 0);
            }
        }

        /* tachometer */
        clip = new rectangle(Machine.visible_area);
        clip.min_x = 5 * 8;
        clip.max_x = clip.min_x + 1;
        for (y = 0; y < 22; y++) {
            int led_color[] = {2, 2, 2, 2, 1, 1, 0, 0, 0, 0, 0};
            int code = ((y / 2) <= turbo_speed) ? 0 : 1;

            drawgfx(bitmap, Machine.gfx[1],
                    code,
                    led_color[y / 2],
                    0, 0,
                    5 * 8, y * 2 + 8,
                    clip, TRANSPARENCY_NONE, 0);
            if (y % 3 == 2) {
                clip.max_x++;
            }
        }

        /* shifter status */
        if ((readinputport(0) & 0x04) != 0) {
            drawgfx(bitmap, Machine.gfx[2], 'H', 0, 0, 0, 2 * 8, 3 * 8, Machine.visible_area, TRANSPARENCY_NONE, 0);
            drawgfx(bitmap, Machine.gfx[2], 'I', 0, 0, 0, 2 * 8, 4 * 8, Machine.visible_area, TRANSPARENCY_NONE, 0);
        } else {
            drawgfx(bitmap, Machine.gfx[2], 'L', 0, 0, 0, 2 * 8, 3 * 8, Machine.visible_area, TRANSPARENCY_NONE, 0);
            drawgfx(bitmap, Machine.gfx[2], 'O', 0, 0, 0, 2 * 8, 4 * 8, Machine.visible_area, TRANSPARENCY_NONE, 0);
        }
    }

    /**
     * *************************************************************************
     *
     * Core drawing routine
     *
     **************************************************************************
     */
    static void draw_everything(mame_bitmap bitmap, int yoffs) {
        UBytePtr overall_priority_base = new UBytePtr(overall_priority, (turbo_fbpla & 8) << 6);
        UBytePtr sprite_priority_base = new UBytePtr(sprite_priority, (turbo_fbpla & 7) << 7);
        UBytePtr road_gfxdata_base = new UBytePtr(road_gfxdata, (turbo_opc << 5) & 0x7e0);
        UShortArray road_palette_base = new UShortArray(road_expanded_palette, (turbo_fbcol & 1) << 4);
        IntArray colortable;
        int x, y, i;

        /* determine the color offset */
        colortable = new IntArray(Machine.pens, (turbo_fbcol & 6) << 6);

        /* loop over rows */
        for (y = 4; y < VIEW_HEIGHT - 4; y++) {
            int sel, coch, babit, slipar_acciar, area, area1, area2, area3, area4, area5, road = 0;
            IntArray sprite_buffer = new IntArray(VIEW_WIDTH + 256);
            IntArray sprite_data = new IntArray(sprite_buffer);
            char[]/*UINT8*/ scanline = new char[VIEW_WIDTH];

            /* compute the Y sum between opa and the current scanline (p. 141) */
            int va  = (y + turbo_opa) & 0xff;

            /* the upper bit of OPC inverts the road */
            if ((turbo_opc & 0x80) == 0) {
                va  ^= 0xff;
            }

            /* clear the sprite buffer and draw the road sprites */
            //memset(sprite_buffer, 0, VIEW_WIDTH * sizeof(UINT32));
            for (i = 0; i < VIEW_WIDTH; i++) {
                sprite_buffer.write(i, 0);
            }
            draw_road_sprites(sprite_buffer, y);
            	
			/* loop over 8-pixel chunks */
			sprite_data.offset += 8;
			for (x = 8; x < VIEW_WIDTH; x += 8)
			{
				int area5_buffer = road_gfxdata_base.read(0x4000 + (x >> 3));
				int/*UINT8*/ back_data = videoram.read((y / 8) * 32 + (x / 8) - 33);
				char backbits_buffer = back_expanded_data[(back_data << 3) | (y & 7)];
	
				/* loop over columns */
				for (i = 0; i < 8; i++)
				{
					int/*UINT32*/ sprite = sprite_data.read();
                                        sprite_data.offset++;
	
					/* compute the X sum between opb and the current column; only the carry matters (p. 141) */
					int carry = (x + i + turbo_opb) >> 8;
	
					/* the carry selects which inputs to use (p. 141) */
					if (carry!=0)
					{
						sel	 = turbo_ipb;
						coch = turbo_ipc >> 4;
					}
					else
					{
						sel	 = turbo_ipa;
						coch = turbo_ipc & 15;
					}
	
					/* at this point we also compute area5 (p. 141) */
					area5 = (area5_buffer >> 3) & 0x10;
					area5_buffer <<= 1;
	
					/* now look up the rest of the road bits (p. 142) */
					area1 = road_gfxdata.read(0x0000 | ((sel & 15) << 8) | va);
					area1 = ((area1 + x + i) >> 8) & 0x01;
					area2 = road_gfxdata.read(0x1000 | ((sel & 15) << 8) | va);
					area2 = ((area2 + x + i) >> 7) & 0x02;
					area3 = road_gfxdata.read(0x2000 | ((sel >> 4) << 8) | va);
					area3 = ((area3 + x + i) >> 6) & 0x04;
					area4 = road_gfxdata.read(0x3000 | ((sel >> 4) << 8) | va);
					area4 = ((area4 + x + i) >> 5) & 0x08;
	
					/* compute the final area value and look it up in IC18/PR1115 (p. 144) */
					area = area5 | area4 | area3 | area2 | area1;
					babit = road_enable_collide.read(area) & 0x07;
	
					/* note: SLIPAR is 0 on the road surface only */
					/*		 ACCIAR is 0 on the road surface and the striped edges only */
					slipar_acciar = road_enable_collide.read(area) & 0x30;
					if (road==0 && (slipar_acciar & 0x20)!=0)
					{
						road = 1;
						draw_offroad_sprites(sprite_buffer, x + i + 2, y);
					}
	
					/* perform collision detection here */
					u8_turbo_collision |= collision_map.read(((sprite >> 24) & 7) | (slipar_acciar >> 1));
	
					/* we only need to continue if we're actually drawing */
					if (bitmap!=null)
					{
						int bacol, red, grn, blu, priority, backbits, mx;
	
						/* also use the coch value to look up color info in IC13/PR1114 and IC21/PR1117 (p. 144) */
						bacol = road_palette_base.read(coch & 15);
	
						/* at this point, do the character lookup */
						backbits = backbits_buffer & 3;
						backbits_buffer >>= 2;
						backbits = back_palette.read(backbits | (back_data & 0xfc));
	
						/* look up the sprite priority in IC11/PR1122 */
						priority = sprite_priority_base.read(sprite >> 25);
	
						/* use that to look up the overall priority in IC12/PR1123 */
						mx = overall_priority_base.read((priority & 7) | ((sprite >> 21) & 8) | ((back_data >> 3) & 0x10) | ((backbits << 2) & 0x20) | (babit << 6));
	
						/* the input colors consist of a mix of sprite, road and 1's & 0's */
						red = 0x040000 | ((bacol & 0x001f) << 13) | ((backbits & 1) << 12) | ((sprite <<  4) & 0x0ff0);
						grn = 0x080000 | ((bacol & 0x03e0) <<  9) | ((backbits & 2) << 12) | ((sprite >>  3) & 0x1fe0);
						blu = 0x100000 | ((bacol & 0x7c00) <<  5) | ((backbits & 4) << 12) | ((sprite >> 10) & 0x3fc0);
	
						/* we then go through a muxer; normally these values are inverted, but */
						/* we've already taken care of that when we generated the palette */
						red = (red >> mx) & 0x10;
						grn = (grn >> mx) & 0x20;
						blu = (blu >> mx) & 0x40;
						scanline[x + i] = (char)(((mx | red | grn | blu))&0xFF);
					}
				}
			}

            /* render the scanline */
            if (bitmap != null) {
                draw_scanline8(bitmap, 8, y + yoffs, VIEW_WIDTH - 8, new UBytePtr(scanline, 8), colortable, -1);
            }
        }
    }
    	
	
	/***************************************************************************
	
	  Master refresh routine
	
	***************************************************************************/
	
    public static VhEofCallbackPtr turbo_vh_eof = new VhEofCallbackPtr() {
        public void handler() {
            /* only do collision checking if we didn't draw */
            if (drew_frame == 0) {
                update_sprite_info();
                draw_everything(null, 0);
            }
            drew_frame = 0;
        }
    };

    public static VhUpdatePtr turbo_vh_screenrefresh = new VhUpdatePtr() {
        public void handler(mame_bitmap bitmap, int full_refresh) {
            /* update the sprite data */
            update_sprite_info();

            /* perform the actual drawing */
            draw_everything(bitmap, 64);

            /* draw the LEDs for the scores */
            draw_scores(bitmap);

            /* indicate that we drew this frame, so that the eof callback doesn't bother doing anything */
            drew_frame = 1;
        }
    };
}
