/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstdlib.rand;

import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;

import static WIP2.mame056.common.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.sound.mixer.*;
import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

import static WIP.mame056.machine.phozon.*;
import static java.lang.Math.abs;

public class phozon
{
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  The palette PROMs are connected to the RGB output this way:
	
	  bit 3 -- 220 ohm resistor  -- RED/GREEN/BLUE
	        -- 470 ohm resistor  -- RED/GREEN/BLUE
	        -- 1  kohm resistor  -- RED/GREEN/BLUE
	  bit 0 -- 2.2kohm resistor  -- RED/GREEN/BLUE
	
	***************************************************************************/
        public static int TOTAL_COLORS(int gfxn){
            return (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity);
        }
        
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])=(char) value;
        }
        
	public static VhConvertColorPromPtr phozon_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
		int _palette = 0;
	
		for (i = 0; i < Machine.drv.total_colors; i++){
			int bit0,bit1,bit2,bit3;
	
			/* red component */
			bit0 = (color_prom.read(0) >> 0) & 0x01;
			bit1 = (color_prom.read(0) >> 1) & 0x01;
			bit2 = (color_prom.read(0) >> 2) & 0x01;
			bit3 = (color_prom.read(0) >> 3) & 0x01;
			palette[_palette++] = (char) (0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
			/* green component */
			bit0 = (color_prom.read(Machine.drv.total_colors) >> 0) & 0x01;
			bit1 = (color_prom.read(Machine.drv.total_colors) >> 1) & 0x01;
			bit2 = (color_prom.read(Machine.drv.total_colors) >> 2) & 0x01;
			bit3 = (color_prom.read(Machine.drv.total_colors) >> 3) & 0x01;
			palette[_palette++] = (char) (0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
			/* blue component */
			bit0 = (color_prom.read(2*Machine.drv.total_colors) >> 0) & 0x01;
			bit1 = (color_prom.read(2*Machine.drv.total_colors) >> 1) & 0x01;
			bit2 = (color_prom.read(2*Machine.drv.total_colors) >> 2) & 0x01;
			bit3 = (color_prom.read(2*Machine.drv.total_colors) >> 3) & 0x01;
			palette[_palette++] = (char) (0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
	
			color_prom.inc();
		}
	
		color_prom.inc( 2*Machine.drv.total_colors );
		/* color_prom now points to the beginning of the lookup table */
	
		/* characters */
		for (i = 0; i < TOTAL_COLORS(0); i++)
			COLOR(colortable,0,i, ((color_prom.readinc()) & 0x0f));
		/* sprites */
		for (i = 0; i < TOTAL_COLORS(2); i++)
			COLOR(colortable,2,i, ((color_prom.readinc()) & 0x0f) + 0x10);
                
                palette[0]=0x00;
                palette[1]=0x00;
                palette[2]=0x00;
                
                palette[3]=0xFF;
                palette[4]=0xFF;
                palette[5]=0xFF;
            }
        };
	
	public static VhStartPtr phozon_vh_start = new VhStartPtr() { public int handler()  {
		/* set up spriteram area */
		spriteram_size[0] = 0x80;
		spriteram = new UBytePtr(phozon_spriteram, 0x780);
		spriteram_2 = new UBytePtr(phozon_spriteram, 0x780+0x800);
		spriteram_3 = new UBytePtr(phozon_spriteram, 0x780+0x800+0x800);
	
		return generic_vh_start.handler();
	} };
	
	public static VhStopPtr phozon_vh_stop = new VhStopPtr() { public void handler()  {
	
		generic_vh_stop.handler();
	} };
	
	public static void phozon_draw_sprite(mame_bitmap dest, int code, int color,
		int flipx,int flipy,int sx,int sy)
	{
            
                code = abs(code);
		drawgfx(dest,Machine.gfx[2],code,color,flipx,flipy,sx,sy,Machine.visible_area,
			TRANSPARENCY_PEN,0);
	}
	
	public static void phozon_draw_sprite8(mame_bitmap dest, int code, int color,
		int flipx,int flipy,int sx,int sy)
	{
		drawgfx(dest,Machine.gfx[3],code,color,flipx,flipy,sx,sy,Machine.visible_area,
			TRANSPARENCY_PEN,0);
	}
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr phozon_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		/* for every character in the video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int sx,sy,mx,my;
	
	
				dirtybuffer[offs] = 0;
	
				/* Even if Phozon screen is 28x36, the memory layout is 32x32. We therefore
				have to convert the memory coordinates into screen coordinates.
				Note that 32*32 = 1024, while 28*36 = 1008: therefore 16 bytes of Video RAM
				don't map to a screen position. We don't check that here, however: range
				checking is performed by drawgfx(). */
	
				mx = offs % 32;
				my = offs / 32;
	
				if (my <= 1)
				{       /* bottom screen characters */
					sx = my + 34;
					sy = mx - 2;
				}
				else if (my >= 30)
				{	/* top screen characters */
					sx = my - 30;
					sy = mx - 2;
				}
				else
				{               /* middle screen characters */
					sx = mx + 2;
					sy = my - 2;
				}
	
				drawgfx(tmpbitmap,Machine.gfx[(colorram.read(offs) & 0x80)!=0 ? 1 : 0],
						videoram.read(offs),
						colorram.read(offs) & 0x3f,
						0,0,
						8*sx,8*sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
				}
		}
	
		copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
                
	
		/* Draw the sprites. */
		for (offs = 0;offs < spriteram_size[0];offs += 2){
			/* is it on? */
			if ((spriteram_3.read(offs+1) & 2) == 0){
				int sprite = spriteram.read(offs);
				int color = spriteram.read(offs+1);
				int x = (spriteram_2.read(offs+1)-69) + 0x100*(spriteram_3.read(offs+1) & 1);
				int y = ( Machine.drv.screen_height ) - spriteram_2.read(offs) - 8;
				int flipx = spriteram_3.read(offs) & 1;
				int flipy = spriteram_3.read(offs) & 2;
	
				switch (spriteram_3.read(offs) & 0x3c)
				{
					case 0x00:		/* 16x16 */
						phozon_draw_sprite(bitmap,sprite,color,flipx,flipy,x,y);
						break;
	
					case 0x14:		/* 8x8 */
						sprite = (sprite << 2) | ((spriteram_3.read(offs) & 0xc0) >> 6);
						phozon_draw_sprite8(bitmap,sprite,color,flipx,flipy,x,y+8);
						break;
	
					case 0x04:		/* 8x16 */
						sprite = (sprite << 2) | ((spriteram_3.read(offs) & 0xc0) >> 6);
						if (flipy == 0){
							phozon_draw_sprite8(bitmap,2+sprite,color,flipx,flipy,x,y+8);
							phozon_draw_sprite8(bitmap,sprite,color,flipx,flipy,x,y);
						}
						else{
							phozon_draw_sprite8(bitmap,2+sprite,color,flipx,flipy,x,y);
							phozon_draw_sprite8(bitmap,sprite,color,flipx,flipy,x,y+8);
						}
						break;
	
					case 0x24:		/* 8x32 */
						sprite = (sprite << 2) | ((spriteram_3.read(offs) & 0xc0) >> 6);
						if (flipy == 0){
							phozon_draw_sprite8(bitmap,10+sprite,color,flipx,flipy,x,y+8);
							phozon_draw_sprite8(bitmap,8+sprite,color,flipx,flipy,x,y);
							phozon_draw_sprite8(bitmap,2+sprite,color,flipx,flipy,x,y-8);
							phozon_draw_sprite8(bitmap,sprite,color,flipx,flipy,x,y-16);
						}
						else{
							phozon_draw_sprite8(bitmap,10+sprite,color,flipx,flipy,x,y-16);
							phozon_draw_sprite8(bitmap,8+sprite,color,flipx,flipy,x,y-8);
							phozon_draw_sprite8(bitmap,2+sprite,color,flipx,flipy,x,y);
							phozon_draw_sprite8(bitmap,sprite,color,flipx,flipy,x,y+8);
						}
						break;
	
					default:
	/*TODO*///#ifdef MAME_DEBUG
	/*TODO*///{
	/*TODO*///char buf[40];
	/*TODO*///sprintf(buf,"%02x",spriteram_3[offs] & 0x3c);
	/*TODO*///usrintf_showmessage(buf);
	/*TODO*///}
	/*TODO*///#endif
						phozon_draw_sprite(bitmap,rand(),color,flipx,flipy,x,y);
						break;
				}
			}
		}
	
	
		/* redraw high priority chars */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if ((colorram.read(offs) & 0x40) != 0)
			{
				int sx,sy,mx,my;
	
	
				/* Even if Phozon screen is 28x36, the memory layout is 32x32. We therefore
				have to convert the memory coordinates into screen coordinates.
				Note that 32*32 = 1024, while 28*36 = 1008: therefore 16 bytes of Video RAM
				don't map to a screen position. We don't check that here, however: range
				checking is performed by drawgfx(). */
	
				mx = offs % 32;
				my = offs / 32;
	
				if (my <= 1)
				{       /* bottom screen characters */
					sx = my + 34;
					sy = mx - 2;
				}
				else if (my >= 30)
				{	/* top screen characters */
					sx = my - 30;
					sy = mx - 2;
				}
				else
				{               /* middle screen characters */
					sx = mx + 2;
					sy = my - 2;
				}
	
				drawgfx(bitmap,Machine.gfx[(colorram.read(offs) & 0x80)!=0 ? 1 : 0],
						videoram.read(offs),
						colorram.read(offs) & 0x3f,
						0,0,
						8*sx,8*sy,
						Machine.visible_area,TRANSPARENCY_PEN,0);
				}
		}
	} };
}
