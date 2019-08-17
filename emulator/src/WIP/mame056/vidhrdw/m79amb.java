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
import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.cpuintrf.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.vidhrdw.generic.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

public class m79amb
{
	
	
	
	/* palette colors (see drivers/8080bw.c) */
	public static final int BLACK = 0;
	public static final int WHITE = 1;
	
	
	
	static int mask = 0;
	
	public static WriteHandlerPtr ramtek_mask_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		mask = data;
	} };
	
	public static WriteHandlerPtr ramtek_videoram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		data = data & ~mask;
	
		if (videoram.read(offset)!= data)
		{
			int i,x,y;
	
			videoram.write(offset,data);
	
			y = offset / 32;
			x = 8 * (offset % 32);
	
			for (i = 0; i < 8; i++)
			{
				plot_pixel2(Machine.scrbitmap, tmpbitmap, x, y, Machine.pens.read((data & 0x80)!=0 ? WHITE : BLACK));
	
				x++;
				data <<= 1;
			}
		}
	} };
}
