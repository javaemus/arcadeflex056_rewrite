/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

import static WIP.mame056.machine.spiders.*;
import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.cpuintrf.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.palette.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.vidhrdw.crtc6845H.*;

public class spiders
{
	
	
	static int[] bitflip = new int[256];
	static int[] screenbuffer;
	
	public static int SCREENBUFFER_SIZE	= 0x2000;
	public static int SCREENBUFFER_MASK	= 0x1fff;
	
	/***************************************************************************
	
	  Start the video hardware emulation.
	
	***************************************************************************/
	public static VhStartPtr spiders_vh_start = new VhStartPtr() { public int handler() 
	{
		int loop;
	
		if ((tmpbitmap = bitmap_alloc(Machine.drv.screen_width,Machine.drv.screen_height)) == null) return 1;
	
		for(loop=0;loop<256;loop++)
		{
			bitflip[loop]=(loop&0x01)!=0?0x80:0x00;
			bitflip[loop]|=(loop&0x02)!=0?0x40:0x00;
			bitflip[loop]|=(loop&0x04)!=0?0x20:0x00;
			bitflip[loop]|=(loop&0x08)!=0?0x10:0x00;
			bitflip[loop]|=(loop&0x10)!=0?0x08:0x00;
			bitflip[loop]|=(loop&0x20)!=0?0x04:0x00;
			bitflip[loop]|=(loop&0x40)!=0?0x02:0x00;
			bitflip[loop]|=(loop&0x80)!=0?0x01:0x00;
		}
	
		if ((screenbuffer = new int[SCREENBUFFER_SIZE]) == null) return 1;
		memset(screenbuffer,1,SCREENBUFFER_SIZE);
	
		return 0;
	} };
	
	
	/***************************************************************************
	
	  Stop the video hardware emulation.
	
	***************************************************************************/
	public static VhStopPtr spiders_vh_stop = new VhStopPtr() { public void handler() 
	{
		bitmap_free(tmpbitmap);
		screenbuffer = null;
	} };
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr spiders_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int loop,data0,data1,data2,col;
	
		int[] crtc6845_mem_size = new int[2];
		int video_addr,increment;
	
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU1));
	
	
		crtc6845_mem_size[0]=crtc6845_horiz_disp*crtc6845_vert_disp*8;
	
		if(spiders_video_flip != 0)
		{
			video_addr=crtc6845_start_addr+(crtc6845_mem_size[0]-1);
			if((video_addr&0xff)==0x80) video_addr-=0x80;	/* Fudge factor!!! */
			increment=-1;
		}
		else
		{
			video_addr=crtc6845_start_addr;
			increment=1;
		}
	
		video_addr&=0xfbff;	/* Fudge factor: sometimes this bit gets set and */
					/* I've no idea how it maps to the hardware but  */
					/* everything works OK if we do this             */
	
		if(crtc6845_page_flip != 0) video_addr+=0x2000;
	
		for(loop=0;loop<crtc6845_mem_size[0];loop++)
		{
			int i,x,y,combo;
	
			if(spiders_video_flip != 0)
			{
				data0=bitflip[RAM.read(0x0000+video_addr)];
				data1=bitflip[RAM.read(0x4000+video_addr)];
				data2=bitflip[RAM.read(0x8000+video_addr)];
			}
			else
			{
				data0=RAM.read(0x0000+video_addr);
				data1=RAM.read(0x4000+video_addr);
				data2=RAM.read(0x8000+video_addr);
			}
	
			combo=data0|(data1<<8)|(data2<<16);
	
			if(screenbuffer[video_addr&SCREENBUFFER_MASK]!=combo)
			{
	
				y=loop/0x20;
	
				for(i=0;i<8;i++)
				{
					x=((loop%0x20)<<3)+i;
					col=((data2&0x01)<<2)+((data1&0x01)<<1)+(data0&0x01);
	
					plot_pixel2(bitmap, tmpbitmap, x, y, Machine.pens.read(col));
	
					data0 >>= 1;
					data1 >>= 1;
					data2 >>= 1;
				}
				screenbuffer[video_addr&SCREENBUFFER_MASK]=combo;
			}
			video_addr+=increment;
			video_addr&=0x3fff;
		}
	
		if (full_refresh != 0)
		{
			/* Now copy the temp bitmap to the screen */
			copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	} };
}
