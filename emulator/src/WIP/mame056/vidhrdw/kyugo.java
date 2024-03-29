/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;

import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.common.libc.cstdio.sprintf;
import static WIP2.mame056.usrintrf.usrintf_showmessage;

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
import static WIP2.mame056.cpuintrfH.*;

public class kyugo
{
	
	
	public static UBytePtr kyugo_videoram = new UBytePtr();
	public static int[] kyugo_videoram_size = new int[1];
	public static UBytePtr kyugo_back_scrollY_lo = new UBytePtr();
	public static UBytePtr kyugo_back_scrollX = new UBytePtr();
	
	static int kyugo_back_scrollY_hi;
	static int palbank,frontcolor;
	static int flipscreen;
	
	
	
	public static WriteHandlerPtr kyugo_gfxctrl_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/* bit 0 is scroll MSB */
		kyugo_back_scrollY_hi = data & 0x01;
	
		/* bit 5 is front layer color (Son of Phoenix only) */
		frontcolor = (data & 0x20) >> 5;
	
		/* bit 6 is background palette bank */
		if (palbank != ((data & 0x40) >> 6))
		{
			palbank = (data & 0x40) >> 6;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	
	if ((data & 0x9e) != 0)
	{
		String baf;
		baf = sprintf("%02x",data);
		usrintf_showmessage(baf);
	}
	} };
	
	
	public static WriteHandlerPtr kyugo_flipscreen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (flipscreen != (data & 0x01))
		{
			flipscreen = (data & 0x01);
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	
	
	static void draw_sprites(mame_bitmap bitmap)
	{
		/* sprite information is scattered through memory */
		/* and uses a portion of the text layer memory (outside the visible area) */
		UBytePtr spriteram_area1 = new UBytePtr(spriteram, 0x28);
		UBytePtr spriteram_area2 = new UBytePtr(spriteram_2, 0x28);
		UBytePtr spriteram_area3 = new UBytePtr(kyugo_videoram, 0x28);
	
		int n;
	
		for( n = 0; n < 12*2; n++ )
		{
			int offs,y,sy,sx,color;
	
			offs = 2*(n % 12) + 64*(n / 12);
	
			sx = spriteram_area3.read(offs+1) + 256 * (spriteram_area2.read(offs+1) & 1);
			if (sx > 320) sx -= 512;
	
			sy = 255 - spriteram_area1.read(offs);
			if (flipscreen!=0) sy = 240 - sy;
	
			color = spriteram_area1.read(offs+1) & 0x1f;
	
			for (y = 0;y < 16;y++)
			{
				int attr2,code,flipx,flipy;
	
				attr2 = spriteram_area2.read(offs + 128*y);
				code = spriteram_area3.read(offs + 128*y);
				if ((attr2 & 0x01)!=0) code += 512;
				if ((attr2 & 0x02)!=0) code += 256;
				flipx =  attr2 & 0x08;
				flipy =  attr2 & 0x04;
				if (flipscreen != 0)
				{
					flipx = flipx!=0?0:1;
					flipy = flipy!=0?0:1;
				}
	
				drawgfx( bitmap, Machine.gfx[1],
						 code,
						 color,
						 flipx,flipy,
						 sx,flipscreen!=0 ? sy - 16*y : sy + 16*y,
						 Machine.visible_area,TRANSPARENCY_PEN, 0 );
			 }
		}
	}
	
	
	
	public static VhUpdatePtr kyugo_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
		UBytePtr color_codes = new UBytePtr(memory_region(REGION_PROMS), 0x300);
	
	
		/* back layer */
		for( offs = videoram_size[0] - 1;offs >= 0;offs-- )
		{
			if ( dirtybuffer[offs] != 0 )
			{
				int sx,sy,tile,flipx,flipy;
	
				dirtybuffer[offs] = 0;
	
				sx = offs % 64;
				sy = offs / 64;
				flipx = colorram.read(offs) & 0x04;
				flipy = colorram.read(offs) & 0x08;
				if (flipscreen != 0)
				{
					sx = 63 - sx;
					sy = 31 - sy;
					flipx = flipx!=0?0:1;
					flipy = flipy!=0?0:1;
				}
	
				tile = videoram.read(offs) + ( 256 * ( colorram.read(offs) & 3 ) );
	
				drawgfx( tmpbitmap, Machine.gfx[2],
						tile,
						((colorram.read(offs) & 0xf0) >> 4) + (palbank << 4),
						flipx,flipy,
						8*sx, 8*sy,
						null,TRANSPARENCY_NONE, 0 );
			}
		}
	
		{
			int scrollx,scrolly;
	
			if (flipscreen!=0)
			{
				scrollx = -32 - ( ( kyugo_back_scrollY_lo.read(0) ) + ( kyugo_back_scrollY_hi * 256 ) );
				scrolly = kyugo_back_scrollX.read(0);
			}
			else
			{
				scrollx = -32 - ( ( kyugo_back_scrollY_lo.read(0) ) + ( kyugo_back_scrollY_hi * 256 ) );
				scrolly = -kyugo_back_scrollX.read(0);
			}
	
			/* copy the temporary bitmap to the screen */
			copyscrollbitmap(bitmap,tmpbitmap,1,new int[]{scrollx},1,new int[]{scrolly},Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	
		/* sprites */
		draw_sprites(bitmap);
	
		/* front layer */
		for( offs = kyugo_videoram_size[0] - 1;offs >= 0;offs-- )
		{
			int sx,sy,code;
	
			sx = offs % 64;
			sy = offs / 64;
			if (flipscreen != 0)
			{
				sx = 35 - sx;
				sy = 31 - sy;
			}
	
			code = kyugo_videoram.read(offs);
	
			drawgfx( bitmap, Machine.gfx[0],
					code,
					2*color_codes.read(code/8) + frontcolor,
					flipscreen, flipscreen,
					8*sx, 8*sy,
					Machine.visible_area,TRANSPARENCY_PEN,0);
		}
	} };
}
