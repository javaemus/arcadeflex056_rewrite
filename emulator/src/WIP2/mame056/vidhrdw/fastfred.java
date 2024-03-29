/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;
import static mame056.palette.*;
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


public class fastfred
{
	
	public static UBytePtr fastfred_attributesram=new UBytePtr();
	static UBytePtr fastfred_color_prom;
	
	
	static rectangle spritevisiblearea = new rectangle
	(
	      2*8, 32*8-1,
	      2*8, 30*8-1
        );
	
	static rectangle spritevisibleareaflipx = new rectangle
	(
	        0*8, 30*8-1,
	        2*8, 30*8-1
        );
	
	static /*unsigned*/ char[] character_bank=new char[2];
	static /*unsigned*/ char[] color_bank=new char[2];
	static int flipscreenx, flipscreeny;
	static int canspritesflipx = 0;
	
        public static InitMachinePtr jumpcoas_init_machine = new InitMachinePtr() {
            public void handler() {
		canspritesflipx = 1;
	}};
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  bit 0 -- 1  kohm resistor  -- RED/GREEN/BLUE
	        -- 470 ohm resistor  -- RED/GREEN/BLUE
	        -- 220 ohm resistor  -- RED/GREEN/BLUE
	  bit 3 -- 100 ohm resistor  -- RED/GREEN/BLUE
	
	***************************************************************************/
	
	static void convert_color(int i, int[] r, int[] g, int[] b)
	{
		int bit0, bit1, bit2, bit3;
		UBytePtr prom = new UBytePtr(fastfred_color_prom);
		int total = Machine.drv.total_colors;
	
		bit0 = (prom.read(i + 0*total) >> 0) & 0x01;
		bit1 = (prom.read(i + 0*total) >> 1) & 0x01;
		bit2 = (prom.read(i + 0*total) >> 2) & 0x01;
		bit3 = (prom.read(i + 0*total) >> 3) & 0x01;
		r[0] = 0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3;
		bit0 = (prom.read(i + 1*total) >> 0) & 0x01;
		bit1 = (prom.read(i + 1*total) >> 1) & 0x01;
		bit2 = (prom.read(i + 1*total) >> 2) & 0x01;
		bit3 = (prom.read(i + 1*total) >> 3) & 0x01;
		g[0] = 0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3;
		bit0 = (prom.read(i + 2*total) >> 0) & 0x01;
		bit1 = (prom.read(i + 2*total) >> 1) & 0x01;
		bit2 = (prom.read(i + 2*total) >> 2) & 0x01;
		bit3 = (prom.read(i + 2*total) >> 3) & 0x01;
		b[0] = 0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3;
	}
        static int TOTAL_COLORS(int gfxn) {
            return Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity;
        }	
	public static VhConvertColorPromPtr fastfred_vh_convert_color_prom = new VhConvertColorPromPtr() { public void handler(char []palette, char []colortable, UBytePtr color_prom) 
	{
	        int i;
	        //#define TOTAL_COLORS(gfxn) (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity)
	        //#define COLOR(gfxn,offs) (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])
	
	
			fastfred_color_prom = new UBytePtr(color_prom);	/* we'll need this later */
                int p_inc = 0;
	        for (i = 0;i < Machine.drv.total_colors;i++)
	        {
	                //int r,g,b;
                        int r[] = new int[1];
                        int g[] = new int[1];
                        int b[] = new int[1];
	
					convert_color(i, r, g, b);
	
					palette[p_inc++]=((char) (r[0]));
					palette[p_inc++]=((char) (g[0]));
					palette[p_inc++]=((char) (b[0]));
	        }
	
	
	        /* characters and sprites use the same palette */
	        for (i = 0;i < TOTAL_COLORS(0);i++)
	        {
				int color;
	
				if ((i & 0x07)==0)
				{
					color = 0;
				}
				else
				{
					color = i;
				}
	
				//COLOR(0,i) = COLOR(1,i) = color;
                                colortable[Machine.drv.gfxdecodeinfo[0].color_codes_start + i] = (char)color;
                                colortable[Machine.drv.gfxdecodeinfo[1].color_codes_start + i] = (char)color;
	        }
	} };
	
	
	public static WriteHandlerPtr fastfred_character_bank_select_w = new WriteHandlerPtr() { public void handler(int offset, int data)
	{
	    if (character_bank[offset] != data)
	    {
	        character_bank[offset] = (char)((data & 1)&0xFF);
	        memset(dirtybuffer, 1, videoram_size[0]);
	
			//if (errorlog && offset==1) fprintf(errorlog, "CharBank = %02X\n", (character_bank[1] << 1) | character_bank[0]);
	    }
	} };
	
	
	public static WriteHandlerPtr fastfred_color_bank_select_w = new WriteHandlerPtr() { public void handler(int offset, int data)
	{
	    if (color_bank[offset] != data)
	    {
	        color_bank[offset] = (char)((data & 1)&0xFF);
	        memset(dirtybuffer, 1, videoram_size[0]);
	
			//if (errorlog && offset==1) fprintf(errorlog, "ColorBank = %02X\n", (color_bank[1] << 1) | color_bank[0]);
	    }
	} };
	
	
	public static WriteHandlerPtr fastfred_background_color_w = new WriteHandlerPtr() { public void handler(int offset, int data)
	{
		//int r,g,b;
                int r[] = new int[1];
                int g[] = new int[1];
                int b[] = new int[1];
	
		//if (errorlog != 0) fprintf(errorlog, "Background color = %02X\n", data);
	
		convert_color(data, r, g, b);
	
		palette_set_color(0,r[0],g[0],b[0]);
	} };
	
	
	public static WriteHandlerPtr fastfred_flipx_w = new WriteHandlerPtr() { public void handler(int offset, int data)
	{
		if (flipscreenx != (data & 1))
		{
			flipscreenx = data & 1;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	public static WriteHandlerPtr fastfred_flipy_w = new WriteHandlerPtr() { public void handler(int offset, int data)
	{
		if (flipscreeny != (data & 1))
		{
			flipscreeny = data & 1;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr fastfred_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
	        int offs, charbank, colorbank;
	
	
		//if (palette_recalc()!=null)
			memset(dirtybuffer,1,videoram_size[0]);
	
	        charbank   = ((character_bank[1] << 9) | (character_bank[0] << 8));
	        colorbank  = ((color_bank[1]     << 4) | (color_bank[0]     << 3));
	
	        for (offs = videoram_size[0] - 1;offs >= 0;offs--)
	        {
					int color;
	
	                if (dirtybuffer[offs]!=0)
	                {
	                        int sx,sy;
	
	                        dirtybuffer[offs] = 0;
	
	                        sx = offs % 32;
	                        sy = offs / 32;
	
				color = colorbank | (fastfred_attributesram.read(2 * sx + 1) & 0x07);
                                	
	                        if (flipscreenx != 0) sx = 31 - sx;
	                        if (flipscreeny != 0) sy = 31 - sy;
	
	                        drawgfx(tmpbitmap,Machine.gfx[0],
	                                charbank | videoram.read(offs),
	                                color,
	                                flipscreenx,flipscreeny,
	                                8*sx,8*sy,
	                                null,TRANSPARENCY_NONE,0);
	                }
	        }
	
	        /* copy the temporary bitmap to the screen */
	        {
	                int i;
                        int[] scroll=new int[32];
	
	
	                if (flipscreenx != 0)
	                {
	                        for (i = 0;i < 32;i++)
	                        {
	                                scroll[31-i] = -fastfred_attributesram.read(2 * i);
	                                if (flipscreeny != 0) scroll[31-i] = -scroll[31-i];
	                        }
	                }
	                else
	                {
	                        for (i = 0;i < 32;i++)
	                        {
	                                scroll[i] = -fastfred_attributesram.read(2 * i);
	                                if (flipscreeny != 0) scroll[i] = -scroll[i];
	                        }
	                }
	
	                copyscrollbitmap(bitmap,tmpbitmap,0,null,32,scroll,Machine.visible_area,TRANSPARENCY_NONE,0);
	        }
	
	
	        /* Draw the sprites */
	        for (offs = spriteram_size[0] - 4;offs >= 0;offs -= 4)
	        {
	                int code,sx,sy,flipx,flipy;
	
	                sx = (spriteram.read(offs + 3) + 1) & 0xff;  /* ??? */
	                sy = 240 - spriteram.read(offs);
	
					if (canspritesflipx != 0)
					{
						// Jump Coaster
						code  =  spriteram.read(offs + 1) & 0x3f;
	                    flipx = ~spriteram.read(offs + 1) & 0x40;
						flipy =  spriteram.read(offs + 1) & 0x80;
					}
					else
					{
						// Fast Freddie
						code  =  spriteram.read(offs + 1) & 0x7f;
						flipx =  0;
						flipy = ~spriteram.read(offs + 1) & 0x80;
					}
	
	
	                if (flipscreenx != 0)
	                {
						sx = 241 - sx;  /* note: 241, not 240 */
						flipx = flipx!=0?0:1;
	                }
	                if (flipscreeny != 0)
	                {
						sy = 240 - sy;
						flipy = flipy!=0?0:1;
	                }
	
	                drawgfx(bitmap,Machine.gfx[1],
	                                code,
	                                colorbank | (spriteram.read(offs + 2) & 0x07),
	                                flipx,flipy,
	                                sx,sy,
	                                flipscreenx!=0 ? spritevisibleareaflipx : spritevisiblearea,TRANSPARENCY_PEN,0);
	        }
	} };
        
        public static WriteHandlerPtr fastfred_attributes_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (((offset & 1)!=0) && (fastfred_attributesram.read(offset) != data))
		{
			int i;
	
	
			for (i = offset / 2;i < videoram_size[0];i += 32)
				dirtybuffer[i] = 1;
		}
	
		fastfred_attributesram.write(offset, data);
	} };
}
