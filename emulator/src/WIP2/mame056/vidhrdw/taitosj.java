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
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuintrf.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.memory.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

public class taitosj
{
	
	public static int MIN(int x, int y){
            return (x)<(y)?(x):(y);
        }
	
	
	public static UBytePtr taitosj_videoram2=new UBytePtr(),taitosj_videoram3=new UBytePtr();
	public static UBytePtr taitosj_characterram=new UBytePtr();
	public static UBytePtr taitosj_scroll=new UBytePtr();
	public static UBytePtr taitosj_colscrolly=new UBytePtr();
	public static UBytePtr taitosj_gfxpointer=new UBytePtr();
	public static UBytePtr taitosj_colorbank=new UBytePtr(),taitosj_video_priority=new UBytePtr();
	static char[] taitosj_collision_reg=new char[4];
	public static UBytePtr dirtybuffer2=new UBytePtr(),dirtybuffer3=new UBytePtr();
	static mame_bitmap[] taitosj_tmpbitmap=new mame_bitmap[3];
        static{
            for (int ii=0 ; ii<3 ; ii++)
                taitosj_tmpbitmap[ii] = new mame_bitmap();
        }
	static mame_bitmap sprite_sprite_collbitmap1,sprite_sprite_collbitmap2;
	static mame_bitmap sprite_plane_collbitmap1;
	static mame_bitmap[] sprite_plane_collbitmap2=new mame_bitmap[3];
	static char[] dirtycharacter1=new char[256],dirtycharacter2=new char[256];
	static char[] dirtysprite1=new char[64],dirtysprite2=new char[64];
	static int taitosj_video_enable;
	static int[] flipscreen=new int[2];
	static rectangle[] spritearea=new rectangle[32]; /*areas on bitmap (sprite locations)*/
        static {
            for (int ii=0 ; ii<32 ; ii++)
                spritearea[ii]=new rectangle();
        }
	static int[] spriteon=new int[32]; /* 1 if sprite is active */
	
	static int playfield_enable_mask[] = { 0x10, 0x20, 0x40 };
	
	
	/***************************************************************************
	
	  I call the three planes with the conventional names "front", "middle" and
	  "back", because that's their default order, but they can be arranged,
	  together with the sprites, in any order. The priority is selected by
	  register 0xd300, which works as follow:
	
	  bits 0-3 go to A4-A7 of a 256x4 PROM
	  bit 4 selects D0/D1 or D2/D3 of the PROM
	  bit 5-7 n.c.
	  A0-A3 of the PROM is fed with a mask of the inactive planes
	  (i.e. all-zero) in the order sprites-front-middle-back
	  the 2-bit code which comes out from the PROM selects the plane
	  to display.
	
	  Here is a dump of one of these PROMs; on the right is the resulting order
	  (s = sprites f = front m = middle b = back). Note that, in theory, the
	  PROM could encode some really funky priority schemes which couldn't be
	  reconducted to the simple layer order given here. Luckily, none of the
	  games seem to do that. Actually, all of them seem to use the same PROM,
	  with the exception of Wild Western.
	
	                                                        d300 pri    d300 pri
	  00: 08 09 08 0A 00 05 00 0F 08 09 08 0A 00 05 00 0F |  00  sfmb    10  msfb
	  10: 08 09 08 0B 00 0D 00 0F 08 09 08 0A 00 05 00 0F |  01  sfbm    11  msbf
	  20: 08 0A 08 0A 04 05 00 0F 08 0A 08 0A 04 05 00 0F |  02  smfb    12  mfsb
	  30: 08 0A 08 0A 04 07 0C 0F 08 0A 08 0A 04 05 00 0F |  03  smbf    13  mfbs
	  40: 08 0B 08 0B 0C 0F 0C 0F 08 09 08 0A 00 05 00 0F |  04  sbfm    14  mbsf
	  50: 08 0B 08 0B 0C 0F 0C 0F 08 0A 08 0A 04 05 00 0F |  05  sbmf    15  mbfs
	  60: 0D 0D 0C 0E 0D 0D 0C 0F 01 05 00 0A 01 05 00 0F |  06  fsmb    16  bsfm
	  70: 0D 0D 0C 0F 0D 0D 0C 0F 01 09 00 0A 01 05 00 0F |  07  fsbm    17  bsmf
	  80: 0D 0D 0E 0E 0D 0D 0C 0F 05 05 02 0A 05 05 00 0F |  08  fmsb    18  bfsm
	  90: 0D 0D 0E 0E 0D 0D 0F 0F 05 05 0A 0A 05 05 00 0F |  09  fmbs    19  bfms
	  A0: 0D 0D 0F 0F 0D 0D 0F 0F 09 09 08 0A 01 05 00 0F |  0A  fbsm    1A  bmsf
	  B0: 0D 0D 0F 0F 0D 0D 0F 0F 09 09 0A 0A 05 05 00 0F |  0B  fbms    1B  bmfs
	  C0: 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F |  0C   -      1C   -
	  D0: 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F |  0D   -      1D   -
	  E0: 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F |  0E   -      1E   -
	  F0: 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F 0F |  0F   -      1F   -
	
	***************************************************************************/
	
	static int[][] draworder=new int[32][4];
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  The Taito games don't have a color PROM. They use RAM to dynamically
	  create the palette. The resolution is 9 bit (3 bits per gun).
	
	  The RAM is connected to the RGB output this way:
	
	  bit 0 -- inverter -- 220 ohm resistor  -- RED
	  bit 7 -- inverter -- 470 ohm resistor  -- RED
	        -- inverter -- 1  kohm resistor  -- RED
	        -- inverter -- 220 ohm resistor  -- GREEN
	        -- inverter -- 470 ohm resistor  -- GREEN
	        -- inverter -- 1  kohm resistor  -- GREEN
	        -- inverter -- 220 ohm resistor  -- BLUE
	        -- inverter -- 470 ohm resistor  -- BLUE
	  bit 0 -- inverter -- 1  kohm resistor  -- BLUE
	
	***************************************************************************/
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])=(char) value;
        }
        
	public static VhConvertColorPromPtr taitosj_vh_convert_color_prom = new VhConvertColorPromPtr() { public void handler(char []palette, char []colortable, UBytePtr color_prom) 
	{
		int i;
		
	
		/* all gfx elements use the same palette */
		for (i = 0;i < 64;i++)
		{
			COLOR(colortable,0,i, i);
			/* we create both a "normal" lookup table and one where pen 0 is */
			/* always mapped to color 0. This is needed for transparency. */
			if (i % 8 == 0) COLOR(colortable,0,i + 64, 0);
			else COLOR(colortable,0,i + 64, i);
		}
	
	
		/* do a simple conversion of the PROM into layer priority order. Note that */
		/* this is a simplification, which assumes the PROM encodes a sensible priority */
		/* scheme. */
		color_prom = memory_region(REGION_PROMS);
		for (i = 0;i < 32;i++)
		{
			int j,mask;
	
			mask = 0;	/* start with all four layers active, so we'll get the highest */
						/* priority one in the first loop */
	
			for (j = 3;j >= 0;j--)
			{
				int data;
	
				data = color_prom.read(0x10 * (i & 0x0f) + mask);
				if ((i & 0x10)!=0) data >>= 2;
				data &= 0x03;
				mask |= (1 << data);	/* in next loop, we'll see which of the remaining */
										/* layers has top priority when this one is transparent */
				draworder[i][j] = data;
			}
		}
	} };
	
	
	
	public static WriteHandlerPtr taitosj_paletteram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int bit0,bit1,bit2;
		int r,g,b,val;
	
	
		paletteram.write(offset,data);
	
		/* red component */
		val = paletteram.read(offset | 1);
		bit0 = (~val >> 6) & 0x01;
		bit1 = (~val >> 7) & 0x01;
		val = paletteram.read(offset & ~1);
		bit2 = (~val >> 0) & 0x01;
		r = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
	
		/* green component */
		val = paletteram.read(offset | 1);
		bit0 = (~val >> 3) & 0x01;
		bit1 = (~val >> 4) & 0x01;
		bit2 = (~val >> 5) & 0x01;
		g = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
	
		/* blue component */
		val = paletteram.read(offset | 1);
		bit0 = (~val >> 0) & 0x01;
		bit1 = (~val >> 1) & 0x01;
		bit2 = (~val >> 2) & 0x01;
		b = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
	
		palette_set_color(offset / 2,r,g,b);
	} };
	
	
	
	/***************************************************************************
	
	  Start the video hardware emulation.
	
	***************************************************************************/
	public static VhStartPtr taitosj_vh_start = new VhStartPtr() { public int handler() 
	{
		int i;
	
	
		taitosj_tmpbitmap[0] = taitosj_tmpbitmap[1] = taitosj_tmpbitmap[2] = null;
		sprite_sprite_collbitmap2 = sprite_sprite_collbitmap1 = null;
		sprite_plane_collbitmap1 = null;
		sprite_plane_collbitmap2[0] = sprite_plane_collbitmap2[1] = sprite_plane_collbitmap2[2] = null;
		dirtybuffer3  = dirtybuffer2 = null;
	
	
		if (generic_vh_start.handler()!= 0)
			return 1;
	
		if ((dirtybuffer2 = new UBytePtr(videoram_size[0])) == null)
		{
			generic_vh_stop.handler();
			return 1;
		}
		memset(dirtybuffer2,1,videoram_size[0]);
	
		if ((dirtybuffer3 = new UBytePtr(videoram_size[0])) == null)
		{
			generic_vh_stop.handler();
			return 1;
		}
		memset(dirtybuffer3,1,videoram_size[0]);
	
		if ((sprite_plane_collbitmap1 = bitmap_alloc(16,16)) == null)
		{
			generic_vh_stop.handler();
			return 1;
		}
	
		for (i = 0; i < 3; i++)
		{
			if ((taitosj_tmpbitmap[i] = bitmap_alloc(Machine.drv.screen_width,Machine.drv.screen_height)) == null)
			{
				generic_vh_stop.handler();
				return 1;
			}
	
			if ((sprite_plane_collbitmap2[i] = bitmap_alloc(Machine.drv.screen_width,Machine.drv.screen_height)) == null)
			{
				generic_vh_stop.handler();
				return 1;
			}
	
		}
	
		if ((sprite_sprite_collbitmap1 = bitmap_alloc(32,32)) == null)
		{
			generic_vh_stop.handler();
			return 1;
		}
	
		if ((sprite_sprite_collbitmap2 = bitmap_alloc(32,32)) == null)
		{
			generic_vh_stop.handler();
			return 1;
		}
	
		flipscreen[0] = flipscreen[1] = 0;
	
		return 0;
	} };
	
	
	
	/***************************************************************************
	
	  Stop the video hardware emulation.
	
	***************************************************************************/
	public static VhStopPtr taitosj_vh_stop = new VhStopPtr() { public void handler() 
	{
		int i;
	
	
		if (sprite_sprite_collbitmap2 != null) bitmap_free(sprite_sprite_collbitmap2);
		if (sprite_sprite_collbitmap1 != null) bitmap_free(sprite_sprite_collbitmap1);
		if (sprite_plane_collbitmap1 != null) bitmap_free(sprite_plane_collbitmap1);
	
		for (i = 0; i < 3; i++)
		{
			if (taitosj_tmpbitmap[i] != null) bitmap_free(taitosj_tmpbitmap[i]);
			if (sprite_plane_collbitmap2[i] != null) bitmap_free(sprite_plane_collbitmap2[i]);
		}
	
		if (dirtybuffer3 != null) dirtybuffer3 = null;
		if (dirtybuffer2 != null) dirtybuffer2 =null;
		generic_vh_stop.handler();
	} };
	
	
	
	public static ReadHandlerPtr taitosj_gfxrom_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int offs;
	
	
		offs = taitosj_gfxpointer.read(0)+taitosj_gfxpointer.read(1)*256;
	
		taitosj_gfxpointer.write(0, taitosj_gfxpointer.read(0)+1);
		if (taitosj_gfxpointer.read(0) == 0) taitosj_gfxpointer.write(1, taitosj_gfxpointer.read(1)+1);
	
		if (offs < 0x8000)
			return new UBytePtr(memory_region(REGION_GFX1)).read(offs);
		else return 0;
	} };
	
	
	public static WriteHandlerPtr taitosj_videoram2_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (taitosj_videoram2.read(offset) != data)
		{
			dirtybuffer2.write(offset, 1);
	
			taitosj_videoram2.write(offset, data);
		}
	} };
	
	
	
	public static WriteHandlerPtr taitosj_videoram3_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (taitosj_videoram3.read(offset) != data)
		{
			dirtybuffer3.write(offset, 1);
	
			taitosj_videoram3.write(offset, data);
		}
	} };
	
	
	
	public static WriteHandlerPtr taitosj_colorbank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (taitosj_colorbank.read(offset) != data)
		{
	logerror("colorbank %d = %02x\n",offset,data);
			memset(dirtybuffer,1,videoram_size[0]);
			memset(dirtybuffer2,1,videoram_size[0]);
			memset(dirtybuffer3,1,videoram_size[0]);
	
			taitosj_colorbank.write(offset, data);
		}
	} };
	
	
	
	public static WriteHandlerPtr taitosj_videoenable_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (taitosj_video_enable != data)
		{
	logerror("videoenable = %02x\n",data);
	
			if ((taitosj_video_enable & 3) != (data & 3))
			{
				flipscreen[0] = data & 1;
				flipscreen[1] = data & 2;
	
				memset(dirtybuffer,1,videoram_size[0]);
				memset(dirtybuffer2,1,videoram_size[0]);
				memset(dirtybuffer3,1,videoram_size[0]);
			}
	
			taitosj_video_enable = data;
		}
	} };
	
	
	
	public static WriteHandlerPtr taitosj_characterram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (taitosj_characterram.read(offset) != data)
		{
			if (offset < 0x1800)
			{
				dirtycharacter1[(offset / 8) & 0xff] = 1;
				dirtysprite1[(offset / 32) & 0x3f] = 1;
			}
			else
			{
				dirtycharacter2[(offset / 8) & 0xff] = 1;
				dirtysprite2[(offset / 32) & 0x3f] = 1;
			}
	
			taitosj_characterram.write(offset, data);
		}
	} };
	
	public static WriteHandlerPtr junglhbr_characterram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		taitosj_characterram_w.handler(offset, data ^ 0xfc);
	} };
	
	/***************************************************************************
	
	  As if the hardware weren't complicated enough, it also has built-in
	  collision detection.
	
	***************************************************************************/
	public static ReadHandlerPtr taitosj_collision_reg_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return taitosj_collision_reg[offset];
	} };
	
	public static WriteHandlerPtr taitosj_collision_reg_clear_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		taitosj_collision_reg[0] = 0;
		taitosj_collision_reg[1] = 0;
		taitosj_collision_reg[2] = 0;
		taitosj_collision_reg[3] = 0;
	} };
	
	public static int get_sprite_xy(int num)
	{
		int offs = num * 4;
	
	
		sx = spriteram.read(offs)- 1 ;
		sy = 240 - spriteram.read(offs + 1);
		return (sy < 240)?1:0;
	}
	
	
	static int check_sprite_sprite_bitpattern(int num1,int num2)
	{
		int x,y,minx,miny,maxx = 16,maxy = 16;
		int offs1 = num1 * 4;
		int offs2 = num2 * 4;
	
		/* normalize coordinates to (0,0) and compute overlap */
		if (sx1 < sx2)
		{
			sx2 -= sx1;
			sx1 = 0;
			minx = sx2;
		}
		else
		{
			sx1 -= sx2;
			sx2 = 0;
			minx = sx1;
		}
	
		if (sy1 < sy2)
		{
			sy2 -= sy1;
			sy1 = 0;
			miny = sy2;
		}
		else
		{
			sy1 -= sy2;
			sy2 = 0;
			miny = sy1;
		}
	
		/* draw the sprites into seperate bitmaps and check overlapping region */
		drawgfx(sprite_sprite_collbitmap1,Machine.gfx[(spriteram.read(offs1 + 3)& 0x40)!=0 ? 3 : 1],
				spriteram.read(offs1 + 3)& 0x3f,
				0,
				spriteram.read(offs1 + 2)& 1, spriteram.read(offs1 + 2)& 2,
				sx1,sy1,
				null,TRANSPARENCY_NONE,0);
	
		drawgfx(sprite_sprite_collbitmap2,Machine.gfx[(spriteram.read(offs2 + 3)& 0x40)!=0 ? 3 : 1],
				spriteram.read(offs2 + 3)& 0x3f,
				0,
				spriteram.read(offs2 + 2)& 1, spriteram.read(offs2 + 2)& 2,
				sx2,sy2,
				null,TRANSPARENCY_NONE,0);
	
		for (y = miny;y < maxy;y++)
		{
			for(x = minx;x < maxx;x++)
			{
				if ((read_pixel.handler(sprite_sprite_collbitmap1, x, y) != Machine.pens.read(0)) &&
				    (read_pixel.handler(sprite_sprite_collbitmap2, x, y) != Machine.pens.read(0)))
				{
					return 1;  /* collided */
				}
			}
		}
	
		return 0;
	}
	
	
	static void check_sprite_sprite_collision()
	{
		int i,j;
	
	
		/* chech each pair of sprites */
		for (i = 0x00; i < 0x20; i++)
		{
			if ((i >= 0x10) && (i <= 0x17)) continue;	/* no sprites here */
	
			if (get_sprite_xy(i) != 0)
			{
				for (j = 0x00; j < 0x20; j++)
				{
					if (j >= i)	 break;		/* only check a pair once and don't check against itself */
	
					if ((j >= 0x10) && (j <= 0x17)) continue;	  /* no sprites here */
	
					if (get_sprite_xy(j) != 0)
					{
						/* rule out any pairs that cannot be touching */
						if ((Math.abs(sx1 - sx2) < 16) &&
							(Math.abs(sy1 - sy2) < 16))
						{
							if (check_sprite_sprite_bitpattern(i, j) != 0)
							{
								/* mark sprites as collided */
								int reg1,reg2;
	
								reg1 = i >> 3;
								if (reg1 == 3)  reg1 = 2;
	
								reg2 = j >> 3;
								if (reg2 == 3)  reg2 = 2;
	
								taitosj_collision_reg[reg1] |= (1 << (i & 0x07));
								taitosj_collision_reg[reg2] |= (1 << (j & 0x07));
							}
						}
					}
				}
			}
		}
	}
	
	
	static void calculate_sprites_areas()
	{
		int i,minx,miny,maxx,maxy;
	
		for (i = 0x00; i < 0x20; i++)
		{
			if ((i >= 0x10) && (i <= 0x17)) continue;	/* no sprites here */
	
			if (get_sprite_xy(i) != 0)
			{
				minx = sx;
				miny = sy;
	
				maxx = minx+15;
				maxy = miny+15;
	
				/* check for bitmap bounds to avoid illegal memory access */
				if (minx < 0) minx = 0;
				if (miny < 0) miny = 0;
				if (maxx >= Machine.drv.screen_width - 1)
					maxx = Machine.drv.screen_width - 1;
				if (maxy >= Machine.drv.screen_height - 1)
					maxy = Machine.drv.screen_height - 1;
	
				spritearea[i].min_x = minx;
				spritearea[i].max_x = maxx;
				spritearea[i].min_y = miny;
				spritearea[i].max_y = maxy;
				spriteon[i] = 1;
			}
			else /* sprite is off */
			{
				spriteon[i] = 0;
			}
		}
	}
	
	static int check_sprite_plane_bitpattern(int num)
	{
		int x,y,minx,miny,maxx,maxy;
		int offs = num * 4;
		int result = 0;  /* no collisions */
	
		int	check_playfield1 = taitosj_video_enable & playfield_enable_mask[0];
		int	check_playfield2 = taitosj_video_enable & playfield_enable_mask[1];
		int	check_playfield3 = taitosj_video_enable & playfield_enable_mask[2];
	
		minx = spritearea[num].min_x;
		miny = spritearea[num].min_y;
		maxx = spritearea[num].max_x + 1;
		maxy = spritearea[num].max_y + 1;
	
	
		flipx = spriteram.read(offs + 2)& 1;
		flipy = spriteram.read(offs + 2)& 2;
	
		if (flipscreen[0]!=0)
		{
			flipx = flipx!=0?0:1;
			minx = MIN(minx + 2, Machine.drv.screen_width);
			maxx = MIN(maxx + 2, Machine.drv.screen_width);
		}
		if (flipscreen[1]!=0)
		{
			flipy = flipy!=0?0:1;;
			miny = MIN(miny + 2, Machine.drv.screen_height);
			maxy = MIN(maxy + 2, Machine.drv.screen_height);
		}
	
		/* draw sprite into a bitmap and check if playfields collide */
		drawgfx(sprite_plane_collbitmap1, Machine.gfx[(spriteram.read(offs + 3)& 0x40)!=0 ? 3 : 1],
				spriteram.read(offs + 3)& 0x3f,
				0,
				flipx, flipy,
				0,0,
				null,TRANSPARENCY_NONE,0);
	
		for (y = miny;y < maxy;y++)
		{
			for (x = minx;x < maxx;x++)
			{
				if (read_pixel.handler(sprite_plane_collbitmap1, x-minx, y-miny) != Machine.pens.read(0)) /* is there anything to check for ? */
				{
					if (check_playfield1!=0 && (read_pixel.handler(sprite_plane_collbitmap2[0], x, y) != Machine.pens.read(0)))
					{
						result |= 1;  /* collided */
						if (result == 7)  return result;//goto done;
						check_playfield1 = 0;
					}
					if (check_playfield2!=0 && (read_pixel.handler(sprite_plane_collbitmap2[1], x, y) != Machine.pens.read(0)))
					{
						result |= 2;  /* collided */
						if (result == 7)  return result;//goto done;
						check_playfield2 = 0;
	
					}
					if (check_playfield3!=0 && (read_pixel.handler(sprite_plane_collbitmap2[2], x, y) != Machine.pens.read(0)))
					{
						result |= 4;  /* collided */
						if (result == 7)  return result;//goto done;
						check_playfield3 = 0;
					}
				}
			}
		}
	
	//done:
		return result;
	}
	
	static void check_sprite_plane_collision()
	{
		int i;
	
	
		/* check each sprite */
		for (i = 0x00; i < 0x20; i++)
		{
			if ((i >= 0x10) && (i <= 0x17)) continue;	/* no sprites here */
	
			if (spriteon[i]!=0)
			{
				taitosj_collision_reg[3] |= check_sprite_plane_bitpattern(i);
			}
		}
	}
	
	static int sx,sy,sx1,sy1,sx2,sy2,flipx,flipy;
        
	static void drawsprites(mame_bitmap bitmap)
	{
		/* Draw the sprites. Note that it is important to draw them exactly in this */
		/* order, to have the correct priorities (but they are still wrong sometimes.) */
		if ((taitosj_video_enable & 0x80) != 0)
		{
			int offs;
	
	
			for (offs = spriteram_size[0] - 4;offs >= 0;offs -= 4)
			{
				
	
	
				if ((offs >= 0x40) && (offs <= 0x5f))  continue;	/* no sprites here */
	
	
				if (get_sprite_xy(offs / 4) != 0)
				{
					flipx = spriteram.read(offs + 2)& 1;
					flipy = spriteram.read(offs + 2)& 2;
					if (flipscreen[0] != 0)
					{
						sx = 238 - sx;
						flipx = flipx!=0?0:1;
					}
					if (flipscreen[1]!=0)
					{
						sy = 242 - sy;
						flipy = flipy!=0?0:1;
					}
	
					drawgfx(bitmap,Machine.gfx[(spriteram.read(offs + 3)& 0x40)!=0 ? 3 : 1],
							spriteram.read(offs + 3)& 0x3f,
							2 * ((taitosj_colorbank.read(1) >> 4) & 0x03) + ((spriteram.read(offs + 2)>> 2) & 1),
							flipx,flipy,
							sx,sy,
							Machine.visible_area,TRANSPARENCY_PEN,0);
	
					/* draw with wrap around. The horizontal games (eg. sfposeid) need this */
					drawgfx(bitmap,Machine.gfx[(spriteram.read(offs + 3)& 0x40)!=0 ? 3 : 1],
							spriteram.read(offs + 3)& 0x3f,
							2 * ((taitosj_colorbank.read(1) >> 4) & 0x03) + ((spriteram.read(offs + 2)>> 2) & 1),
							flipx,flipy,
							sx - 0x100,sy,
							Machine.visible_area,TRANSPARENCY_PEN,0);
				}
			}
		}
	}
	
        static int fudge1[] = { 3,  1, -1 };
	static int fudge2[] = { 8, 10, 12 };
	
	static void drawplayfield(int n, mame_bitmap bitmap)
	{
		
	
		if ((taitosj_video_enable & playfield_enable_mask[n]) != 0)
		{
			int i=0, scrollx=0;
                        int[] scrolly=new int[32];
	
			scrollx = taitosj_scroll.read(2*n);
			if (flipscreen[0] != 0)
				scrollx =  (scrollx & 0xf8) + ((scrollx + fudge1[n]) & 7) + fudge2[n];
			else
				scrollx = -(scrollx & 0xf8) + ((scrollx + fudge1[n]) & 7) + fudge2[n];
	
			if (flipscreen[1] != 0)
			{
				for (i = 0;i < 32;i++)
					scrolly[31-i] = taitosj_colscrolly.read(32*n+i) + taitosj_scroll.read(2*n+1);
			}
			else
			{
				for (i = 0;i < 32;i++)
					scrolly[i]    = -taitosj_colscrolly.read(32*n+i) - taitosj_scroll.read(2*n+1);
			}
                        
                        copyscrollbitmap(bitmap,taitosj_tmpbitmap[n],1,new int[]{(scrollx)},32,scrolly,Machine.visible_area,TRANSPARENCY_COLOR,0);
	
			/* store parts covered with sprites for sprites/playfields collision detection */
			for (i=0x00; i<0x20; i++)
			{
				if ((i >= 0x10) && (i <= 0x17)) continue; /* no sprites here */
				if (spriteon[i] != 0)
					copyscrollbitmap( sprite_plane_collbitmap2[n],taitosj_tmpbitmap[n],1,new int[]{scrollx},32,scrolly,spritearea[i],TRANSPARENCY_NONE,0);
			}
		}
	}
	
	
	static void drawplane(int n,mame_bitmap bitmap)
	{
		switch (n)
		{
		case 0:
			drawsprites(bitmap);
			break;
		case 1:
		case 2:
		case 3:
			drawplayfield(n-1,bitmap);
			break;
		}
	}
	
	
	public static VhUpdatePtr taitosj_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs,i;
	
	
		/* decode modified characters */
		for (offs = 0;offs < 256;offs++)
		{
			if (dirtycharacter1[offs] == 1)
			{
				decodechar(Machine.gfx[0],offs,taitosj_characterram,Machine.drv.gfxdecodeinfo[0].gfxlayout);
				dirtycharacter1[offs] = 0;
			}
			if (dirtycharacter2[offs] == 1)
			{
				decodechar(Machine.gfx[2],offs,new UBytePtr(taitosj_characterram, 0x1800),Machine.drv.gfxdecodeinfo[2].gfxlayout);
				dirtycharacter2[offs] = 0;
			}
		}
		/* decode modified sprites */
		for (offs = 0;offs < 64;offs++)
		{
			if (dirtysprite1[offs] == 1)
			{
				decodechar(Machine.gfx[1],offs,new UBytePtr(taitosj_characterram),Machine.drv.gfxdecodeinfo[1].gfxlayout);
				dirtysprite1[offs] = 0;
			}
			if (dirtysprite2[offs] == 1)
			{
				decodechar(Machine.gfx[3],offs,new UBytePtr(taitosj_characterram, 0x1800),Machine.drv.gfxdecodeinfo[3].gfxlayout);
				dirtysprite2[offs] = 0;
			}
		}
	
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int sx,sy;
	
	
				dirtybuffer[offs] = 0;
	
				sx = offs % 32;
				sy = offs / 32;
				if (flipscreen[0] != 0) sx = 31 - sx;
				if (flipscreen[1] != 0) sy = 31 - sy;
	
				drawgfx(taitosj_tmpbitmap[0],Machine.gfx[(taitosj_colorbank.read(0) & 0x08) != 0 ? 2 : 0],
						videoram.read(offs),
						(taitosj_colorbank.read(0) & 0x07) + 8,	/* use transparent pen 0 */
						flipscreen[0],flipscreen[1],
						8*sx,8*sy,
						null,TRANSPARENCY_NONE,0);
			}
	
			if (dirtybuffer2.read(offs)!=0)
			{
				int sx,sy;
	
	
				dirtybuffer2.write(offs, 0);
	
				sx = offs % 32;
				sy = offs / 32;
				if (flipscreen[0]!=0) sx = 31 - sx;
				if (flipscreen[1]!=0) sy = 31 - sy;
	
				drawgfx(taitosj_tmpbitmap[1],Machine.gfx[(taitosj_colorbank.read(0) & 0x80)!=0 ? 2 : 0],
						taitosj_videoram2.read(offs),
						((taitosj_colorbank.read(0) >> 4) & 0x07) + 8,	/* use transparent pen 0 */
						flipscreen[0],flipscreen[1],
						8*sx,8*sy,
						null,TRANSPARENCY_NONE,0);
			}
	
			if (dirtybuffer3.read(offs)!=0)
			{
				int sx,sy;
	
	
				dirtybuffer3.write(offs, 0);
	
				sx = offs % 32;
				sy = offs / 32;
				if (flipscreen[0]!=0) sx = 31 - sx;
				if (flipscreen[1]!=0) sy = 31 - sy;
	
				drawgfx(taitosj_tmpbitmap[2],Machine.gfx[(taitosj_colorbank.read(1) & 0x08)!=0 ? 2 : 0],
						taitosj_videoram3.read(offs),
						(taitosj_colorbank.read(1) & 0x07) + 8,	/* use transparent pen 0 */
						flipscreen[0],flipscreen[1],
						8*sx,8*sy,
						null,TRANSPARENCY_NONE,0);
			}
		}
	
	
		/*called here because drawplayfield() uses its output (spritearea[32]) */
		calculate_sprites_areas();
	
		/* first of all, fill the screen with the background color */
		fillbitmap(bitmap,Machine.pens.read(8 * (taitosj_colorbank.read(1) & 0x07)),
				Machine.visible_area);
	
		for (i = 0;i < 4;i++)
			drawplane(draworder[taitosj_video_priority.read() & 0x1f][i],bitmap);
	
	
		check_sprite_sprite_collision();
	
		/*check_sprite_plane_collision() uses drawn bitmaps, so it must me called _AFTER_ drawplane() */
		check_sprite_plane_collision();
	
		/*check_plane_plane_collision();*/	/*not implemented !!!*/
	} };
}
