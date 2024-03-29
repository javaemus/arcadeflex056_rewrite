/***************************************************************************

 COSMIC.C

 emulation of video hardware of cosmic machines of 1979-1980(ish)

***************************************************************************/

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

public class cosmic
{
	public static abstract interface MapColorHandlerPtr {
            public abstract int handler(int x, int y);
        }
	
	public static MapColorHandlerPtr map_color = null;
	
	static int[] color_registers=new int[3];
	static int color_base = 0;
	static int nomnlnd_background_on=0;
	
	
	/* No Mans Land - I don't know if there are more screen layouts than this */
	/* this one seems to be OK for the start of the game       */
	
	static int[][] nomnlnd_tree_positions =
	{
		{66,63},{66,159}
	};
	
	static int nomnlnd_water_positions[][] =
	{
		{160,32},{160,96},{160,128},{160,192}
	};
	
	
	public static WriteHandlerPtr panic_color_register_w = new WriteHandlerPtr() {
            public void handler(int offset, int data)
            {
		/* 7c0c & 7c0e = Rom Address Offset
	 	   7c0d        = high / low nibble */
	
		set_vh_global_attribute(new int[]{color_registers[offset]}, data & 0x80);
                color_registers[offset] = data & 0x80;
	   	color_base = (color_registers[0] << 2) + (color_registers[2] << 3);
            } 
        };
	
	public static WriteHandlerPtr cosmicg_color_register_w = new WriteHandlerPtr() {
            public void handler(int offset, int data)
            {
		set_vh_global_attribute(new int[]{color_registers[offset]}, data);
                color_registers[offset] = data;
	   	color_base = (color_registers[0] << 8) + (color_registers[1] << 9);
            } 
        };
	
	
	public static MapColorHandlerPtr panic_map_color = new MapColorHandlerPtr() {
            public int handler(int x, int y) {
                /* 8 x 16 coloring */
		int _byte = memory_region(REGION_USER1).read(color_base + (x / 16) * 32 + (y / 8));
	
		if (color_registers[1] != 0)
			return (_byte >> 4);
		else
			return (_byte & 0x0f);
            }
        };
	
	static MapColorHandlerPtr cosmicg_map_color = new MapColorHandlerPtr() {
            public int handler(int x, int y) {
		int _byte;
	
		/* 16 x 16 coloring */
		_byte = memory_region(REGION_USER1).read(color_base + (y / 16) * 16 + (x / 16));
	
		/* the upper 4 bits are for cocktail mode support */
	
		return (_byte & 0x0f);
            }
        };
	
	static MapColorHandlerPtr magspot2_map_color = new MapColorHandlerPtr() {
            public int handler(int x, int y) {
		int _byte;
	
		/* 16 x 8 coloring */
	
		// Should the top line of the logo be red or white???
	
		_byte = memory_region(REGION_USER1).read((x / 8) * 16 + (y / 16));
	
		if (color_registers[1] != 0)
			return (_byte >> 4);
		else
			return (_byte & 0x0f);
            }
        };
	
	static char panic_remap_sprite_code[][] =
	{
	{0x00,0},{0x26,0},{0x25,0},{0x24,0},{0x23,0},{0x22,0},{0x21,0},{0x20,0}, /* 00 */
	{0x00,0},{0x26,0},{0x25,0},{0x24,0},{0x23,0},{0x22,0},{0x21,0},{0x20,0}, /* 08 */
	{0x00,0},{0x16,0},{0x15,0},{0x14,0},{0x13,0},{0x12,0},{0x11,0},{0x10,0}, /* 10 */
	{0x00,0},{0x16,0},{0x15,0},{0x14,0},{0x13,0},{0x12,0},{0x11,0},{0x10,0}, /* 18 */
	{0x00,0},{0x06,0},{0x05,0},{0x04,0},{0x03,0},{0x02,0},{0x01,0},{0x00,0}, /* 20 */
	{0x00,0},{0x06,0},{0x05,0},{0x04,0},{0x03,0},{0x02,0},{0x01,0},{0x00,0}, /* 28 */
	{0x07,2},{0x06,2},{0x05,2},{0x04,2},{0x03,2},{0x02,2},{0x01,2},{0x00,2}, /* 30 */
	{0x07,2},{0x06,2},{0x05,2},{0x04,2},{0x03,2},{0x02,2},{0x01,2},{0x00,2}, /* 38 */
	};
	
	/*
	 * Panic Color table setup
	 *
	 * Bit 0 = RED, Bit 1 = GREEN, Bit 2 = BLUE
	 *
	 * First 8 colors are normal intensities
	 *
	 * But, bit 3 can be used to pull Blue via a 2k resistor to 5v
	 * (1k to ground) so second version of table has blue set to 2/3
	 *
	 */
        
        public static int TOTAL_COLORS(int gfxn){
            return (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity);
        }
        
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs]) = (char) value;
        }
	
	public static VhConvertColorPromPtr panic_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
		int _palette = 0;
	
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
			palette[_palette++] = (char) (0xff * ((i >> 0) & 1));
			palette[_palette++] = (char) (0xff * ((i >> 1) & 1));
			if ((i & 0x0c) == 0x08)
				palette[_palette++] = 0xaa;
			else
				palette[_palette++] = (char) (0xff * ((i >> 2) & 1));
		}
	
	
		for (i = 0;i < TOTAL_COLORS(0);i++){
			COLOR(colortable,0,i,color_prom.read() & 0x0f);
                        color_prom.inc();
                }
	
	
	    map_color = panic_map_color;
            }
        };
	
	/*
	 * Cosmic Alien Color table setup
	 *
	 * 8 colors, 16 sprite color codes
	 *
	 * Bit 0 = RED, Bit 1 = GREEN, Bit 2 = BLUE
	 *
	 */
	
	public static VhConvertColorPromPtr cosmica_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
                int _palette = 0;
	
		//#define TOTAL_COLORS(gfxn) (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity)
		//#define COLOR(gfxn,offs) (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])
	
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
			palette[_palette++] = (char) (0xff * ((i >> 0) & 1));
			palette[_palette++] = (char) (0xff * ((i >> 1) & 1));
			palette[_palette++] = (char) (0xff * ((i >> 2) & 1));
		}
	
	
		for (i = 0;i < TOTAL_COLORS(0)/2;i++)
		{
			COLOR(colortable,0,i,color_prom.read() & 0x07);
			COLOR(colortable,0,i+(TOTAL_COLORS(0)/2), ((color_prom.read()) >> 4) & 0x07);
                        color_prom.inc();
		}
	
	
	    map_color = panic_map_color;
            }
        };
	
	/*
	 * Cosmic guerilla table setup
	 *
	 * Use AA for normal, FF for Full Red
	 * Bit 0 = R, bit 1 = G, bit 2 = B, bit 4 = High Red
	 *
	 * It's possible that the background is dark gray and not black, as the
	 * resistor chain would never drop to zero, Anybody know ?
	 *
	 */
	
	public static VhConvertColorPromPtr cosmicg_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
                int _palette = 0;
	
		//#define TOTAL_COLORS(gfxn) (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity)
		//#define COLOR(gfxn,offs) (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])
	
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
	    	if (i > 8) palette[_palette++] = 0xff;
	        else palette[_palette++] = (char) (0xaa * ((i >> 0) & 1));
	
			palette[_palette++] = (char) (0xaa * ((i >> 1) & 1));
			palette[_palette++] = (char) (0xaa * ((i >> 2) & 1));
		}
	
	
	    map_color = cosmicg_map_color;
            }
        };
	
	/**************************************************/
	/* Magical Spot 2/Devil Zone specific routines    */
	/*												  */
	/* 16 colors, 8 sprite color codes				  */
	/**************************************************/
	
	public static VhConvertColorPromPtr magspot2_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
                int _palette = 0;
		//#define TOTAL_COLORS(gfxn) (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity)
		//#define COLOR(gfxn,offs) (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])
	
	
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
			if ((i & 0x09) == 0x08)
				palette[_palette++] = 0xaa;
		 	else
				palette[_palette++] = (char) (0xff * ((i >> 0) & 1));
	
			palette[_palette++] = (char) (0xff * ((i >> 1) & 1));
			palette[_palette++] = (char) (0xff * ((i >> 2) & 1));
		}
	
	
		for (i = 0;i < TOTAL_COLORS(0);i++)
		{
			COLOR(colortable,0,i,color_prom.read() & 0x0f);
                        color_prom.inc();
		}
	
	
	    map_color = magspot2_map_color;
            }
        };
	
	
	public static WriteHandlerPtr nomnlnd_background_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		nomnlnd_background_on = data;
	} };
	
	
	public static WriteHandlerPtr cosmica_videoram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    int i,x,y,col;
	
	    videoram.write(offset, data);
	
		y = offset / 32;
		x = 8 * (offset % 32);
	
	    col = Machine.pens.read(map_color.handler(x, y));
	
	    for (i = 0; i < 8; i++)
	    {
			if (flip_screen() != 0)
				plot_pixel.handler(tmpbitmap, 255-x, 255-y, (data & 0x80)!=0 ? col : Machine.pens.read(0));
			else
				plot_pixel.handler(tmpbitmap,     x,     y, (data & 0x80)!=0 ? col : Machine.pens.read(0));
	
		    x++;
		    data <<= 1;
	    }
	} };
	
	
	public static VhUpdatePtr cosmicg_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		if (full_refresh != 0)
		{
			int offs;
	
			for (offs = 0; offs < videoram_size[0]; offs++)
			{
				cosmica_videoram_w.handler(offs, videoram.read(offs));
			}
		}
	
		copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
	} };
	
	
	public static VhUpdatePtr panic_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		cosmicg_vh_screenrefresh.handler(bitmap, full_refresh);
	
	
	    /* draw the sprites */
	
		for (offs = spriteram_size[0] - 4;offs >= 0;offs -= 4)
		{
			if (spriteram.read(offs) != 0)
			{
				int code,bank,flipy;
	
				/* panic_remap_sprite_code sprite number to my layout */
	
				code = panic_remap_sprite_code[(spriteram.read(offs) & 0x3F)][0];
				bank = panic_remap_sprite_code[(spriteram.read(offs) & 0x3F)][1];
				flipy = spriteram.read(offs) & 0x40;
	
				if((code==0) && (bank==0))
					logerror("remap failure %2x\n",(spriteram.read(offs) & 0x3F));
	
				/* Switch Bank */
	
				if((spriteram.read(offs+3) & 0x08) != 0) bank=1;
	
				if (flip_screen() != 0)
				{
					flipy = flipy!=0?0:1;
				}
	
				drawgfx(bitmap,Machine.gfx[bank],
						code,
						7 - (spriteram.read(offs+3) & 0x07),
						flip_screen(),flipy,
						256-spriteram.read(offs+2),spriteram.read(offs+1),
						Machine.visible_area,TRANSPARENCY_PEN,0);
			}
		}
	} };
	
	
	public static VhUpdatePtr cosmica_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		cosmicg_vh_screenrefresh.handler(bitmap, full_refresh);
	
	
	    /* draw the sprites */
	
		for (offs = spriteram_size[0] - 4;offs >= 0;offs -= 4)
		{
			if (spriteram.read(offs) != 0)
	        {
				int code, color;
	
				code  = ~spriteram.read(offs  ) & 0x3f;
				color = ~spriteram.read(offs+3) & 0x0f;
	
	            if ((spriteram.read(offs) & 0x80) != 0)
	            {
	                /* 16x16 sprite */
	
				    drawgfx(bitmap,Machine.gfx[0],
						    code,
						    color,
						    0,0,
					    	256-spriteram.read(offs+2),spriteram.read(offs+1),
					        Machine.visible_area,TRANSPARENCY_PEN,0);
	            }
	            else
	            {
	                /* 32x32 sprite */
	
				    drawgfx(bitmap,Machine.gfx[1],
						    code >> 2,
						    color,
						    0,0,
					    	256-spriteram.read(offs+2),spriteram.read(offs+1),
					        Machine.visible_area,TRANSPARENCY_PEN,0);
	            }
	        }
		}
	} };
	
	
	public static VhUpdatePtr magspot2_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		cosmicg_vh_screenrefresh.handler(bitmap, full_refresh);
	
	
	    /* draw the sprites */
	
		for (offs = spriteram_size[0] - 4;offs >= 0;offs -= 4)
		{
			if (spriteram.read(offs) != 0)
	        {
				int code, color;
	
				code  = ~spriteram.read(offs  ) & 0x3f;
				color = ~spriteram.read(offs+3) & 0x07;
	
	            if ((spriteram.read(offs) & 0x80) != 0)
	            {
	                /* 16x16 sprite */
	
				    drawgfx(bitmap,Machine.gfx[0],
						    code,
						    color,
						    0,0,
					    	256-spriteram.read(offs+2),spriteram.read(offs+1),
					        Machine.visible_area,TRANSPARENCY_PEN,0);
	            }
	            else
	            {
	                /* 32x32 sprite */
	
				    drawgfx(bitmap,Machine.gfx[1],
						    code >> 2,
						    color,
						    0,0,
					    	256-spriteram.read(offs+2),spriteram.read(offs+1),
					        Machine.visible_area,TRANSPARENCY_PEN,0);
	            }
	        }
		}
	} };
	
	
	public static VhUpdatePtr nomnlnd_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		magspot2_vh_screenrefresh.handler(bitmap, full_refresh);
	
	
	    if (nomnlnd_background_on != 0)
	    {
			// draw trees
	
	        int water_animate=0;
	
	        water_animate++;
	
	    	for(offs=0;offs<2;offs++)
	        {
				int code,x,y;
	
				x = nomnlnd_tree_positions[offs][0];
				y = nomnlnd_tree_positions[offs][1];
	
				if (flip_screen() != 0)
				{
					x = 223 - x;
					y = 223 - y;
					code = 2 + offs;
				}
				else
				{
					code = offs;
				}
	
	    		drawgfx(bitmap,Machine.gfx[2],
						code,
						8,
						0,0,
						x,y,
						Machine.visible_area,TRANSPARENCY_PEN,0);
	        }
	
			// draw water
	
	    	for(offs=0;offs<4;offs++)
	        {
				int x,y;
	
				x = nomnlnd_water_positions[offs][0];
				y = nomnlnd_water_positions[offs][1];
	
				if (flip_screen() != 0)
				{
					x = 239 - x;
					y = 223 - y;
				}
	
	    		drawgfx(bitmap,Machine.gfx[3],
						water_animate >> 3,
						9,
						0,0,
						x,y,
						Machine.visible_area,TRANSPARENCY_NONE,0);
	        }
	    }
	} };
}
