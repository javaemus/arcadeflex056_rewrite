/***************************************************************************

  Cloud9 (prototype) driver.

  This hardware is yet another variant of the Centipede/Millipede hardware,
  but as you can see there are some significant deviations...

  0000			R/W 	X index into the bitmap
  0001			R/W 	Y index into the bitmap
  0002			R/W 	Current bitmap pixel value
  0003-05FF 	R/W 	RAM
  0600-3FFF 	R/W 	Bitmap RAM bank 0 (and bank 1 ?)
  5000-5073 	R/W 	Motion Object RAM
  5400			W		Watchdog
  5480			W		IRQ Acknowledge
  5500-557F 	W		Color RAM (9 bits, 4 banks, LSB of Blue is addr&$40)

  5580			W		Auto-increment X bitmap index (~D7)
  5581			W		Auto-increment Y bitmap index (~D7)
  5584			W		VRAM Both Banks - (D7) seems to allow writing to both banks
  5585			W		Invert screen?
  5586			W		VRAM Bank select?
  5587			W		Color bank select

  5600			W		Coin Counter 1 (D7)
  5601			W		Coin Counter 2 (D7)
  5602			W		Start1 LED (~D7)
  5603			W		Start2 LED (~D7)

  5680			W		Force Write to EAROM?
  5700			W		EAROM Off?
  5780			W		EAROM On?

  5800			R		IN0 (D7=Vblank, D6=Right Coin, D5=Left Coin, D4=Aux, D3=Self Test)
  5801			R		IN1 (D7=Start1, D6=Start2, D5=Fire, D4=Zap)
  5900			R		Trackball Vert
  5901			R		Trackball Horiz

  5A00-5A0F 	R/W 	Pokey 1
  5B00-5B0F 	R/W 	Pokey 2
  5C00-5CFF 	W		EAROM
  6000-FFFF 	R		Program ROM



If you have any questions about how this driver works, don't hesitate to
ask.  - Mike Balfour (mab22@po.cwru.edu)
***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.vidhrdw.cloud9.*;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;
import static WIP2.mame056.sound.mixer.*;
import static WIP2.mame056.sound.mixerH.*;
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.sound.pokey.*;
import static WIP2.mame056.sound.pokeyH.*;

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.palette.*;

public class cloud9
{
	
	static UBytePtr nvram = new UBytePtr();
	static int[] nvram_size=new int[2];
	
	static nvramPtr nvram_handler = new nvramPtr() {
            public void handler(Object file, int read_or_write) {
                if (read_or_write != 0)
			osd_fwrite(file,nvram,nvram_size[0]);
		else
		{
			if (file != null)
				osd_fread(file,nvram,nvram_size[0]);
			else
				memset(nvram,0,nvram_size[0]);
		}
            }
        };
	
	public static WriteHandlerPtr cloud9_led_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/*TODO*///set_led_status(offset,~data & 0x80);
	} };
	
	public static WriteHandlerPtr cloud9_coin_counter_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_counter_w.handler(offset,data);
	} };
	
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x0002, cloud9_bitmap_regs_r ),
		new Memory_ReadAddress( 0x0003, 0x05ff, MRA_RAM ),
		new Memory_ReadAddress( 0x0600, 0x3fff, MRA_RAM ),
		new Memory_ReadAddress( 0x5500, 0x557f, MRA_RAM ),
		new Memory_ReadAddress( 0x5800, 0x5800, input_port_0_r ),
		new Memory_ReadAddress( 0x5801, 0x5801, input_port_1_r ),
		new Memory_ReadAddress( 0x5900, 0x5900, input_port_2_r ),
		new Memory_ReadAddress( 0x5901, 0x5901, input_port_3_r ),
		new Memory_ReadAddress( 0x5a00, 0x5a0f, pokey1_r ),
		new Memory_ReadAddress( 0x5b00, 0x5b0f, pokey2_r ),
		new Memory_ReadAddress( 0x5c00, 0x5cff, MRA_RAM ),	/* EAROM */
		new Memory_ReadAddress( 0x6000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x0002, cloud9_bitmap_regs_w, cloud9_bitmap_regs ),
		new Memory_WriteAddress( 0x0003, 0x05ff, MWA_RAM ),
		new Memory_WriteAddress( 0x0600, 0x3fff, cloud9_bitmap_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x5000, 0x50ff, MWA_RAM, spriteram ),
		new Memory_WriteAddress( 0x5400, 0x5400, watchdog_reset_w ),
		new Memory_WriteAddress( 0x5480, 0x5480, MWA_NOP ),	/* IRQ Ack */
		new Memory_WriteAddress( 0x5500, 0x557f, cloud9_paletteram_w, paletteram ),
		new Memory_WriteAddress( 0x5580, 0x5580, MWA_RAM, cloud9_auto_inc_x ),
		new Memory_WriteAddress( 0x5581, 0x5581, MWA_RAM, cloud9_auto_inc_y ),
		new Memory_WriteAddress( 0x5584, 0x5584, MWA_RAM, cloud9_both_banks ),
		new Memory_WriteAddress( 0x5586, 0x5586, MWA_RAM, cloud9_vram_bank ),
		new Memory_WriteAddress( 0x5587, 0x5587, MWA_RAM, cloud9_color_bank ),
		new Memory_WriteAddress( 0x5600, 0x5601, cloud9_coin_counter_w ),
		new Memory_WriteAddress( 0x5602, 0x5603, cloud9_led_w ),
		new Memory_WriteAddress( 0x5a00, 0x5a0f, pokey1_w ),
		new Memory_WriteAddress( 0x5b00, 0x5b0f, pokey2_w ),
		new Memory_WriteAddress( 0x5c00, 0x5cff, MWA_RAM, nvram, nvram_size ),
		new Memory_WriteAddress( 0x6000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress( 0x10600,0x13fff, MWA_RAM, cloud9_vram2 ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	
	static InputPortPtr input_ports_cloud9 = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* IN0 */
		PORT_BIT ( 0x07, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_SERVICE( 0x08, IP_ACTIVE_LOW );
		PORT_BIT ( 0x10, IP_ACTIVE_LOW, IPT_COIN3 );
		PORT_BIT ( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT ( 0x40, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT ( 0x80, IP_ACTIVE_LOW, IPT_VBLANK );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT ( 0x0F, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT ( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT ( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT ( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT ( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN2 */
		PORT_ANALOG( 0xff, 0x7f, IPT_TRACKBALL_Y | IPF_REVERSE, 30, 30, 0, 0 );
	
		PORT_START(); 		/* IN3 */
		PORT_ANALOG( 0xff, 0x7f, IPT_TRACKBALL_X, 30, 30, 0, 0 );
	
		PORT_START(); 	/* IN4 */ /* DSW1 */
		PORT_BIT ( 0xFF, IP_ACTIVE_HIGH, IPT_UNKNOWN );
	
		PORT_START(); 	/* IN5 */ /* DSW2 */
		PORT_BIT ( 0x01, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_DIPNAME( 0x06, 0x04, DEF_STR( "Coinage") );
		PORT_DIPSETTING (	0x06, DEF_STR( "2C_1C") );
		PORT_DIPSETTING (	0x04, DEF_STR( "1C_1C") );
		PORT_DIPSETTING (	0x02, DEF_STR( "1C_2C") );
		PORT_DIPSETTING (	0x00, DEF_STR( "Free_Play") );
		PORT_DIPNAME(0x18,	0x00, "Right Coin" );
		PORT_DIPSETTING (	0x00, "*1" );
		PORT_DIPSETTING (	0x08, "*4" );
		PORT_DIPSETTING (	0x10, "*5" );
		PORT_DIPSETTING (	0x18, "*6" );
		PORT_DIPNAME(0x20,	0x00, "Middle Coin" );
		PORT_DIPSETTING (	0x00, "*1" );
		PORT_DIPSETTING (	0x20, "*2" );
		PORT_DIPNAME(0xC0,	0x00, "Bonus Coins" );
		PORT_DIPSETTING (	0xC0, "4 coins + 2 coins" );
		PORT_DIPSETTING (	0x80, "4 coins + 1 coin" );
		PORT_DIPSETTING (	0x40, "2 coins + 1 coin" );
		PORT_DIPSETTING (	0x00, "None" );
	INPUT_PORTS_END(); }}; 
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		128,	/* 128 characters */
		4,	/* 4 bits per pixel */
		new int[] { 0x3000*8, 0x2000*8, 0x1000*8, 0 },	/* the four bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		16*8	/* every char takes 8 consecutive bytes, then skip 8 */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,	/* 16*16 sprites */
		64, /* 64 sprites */
		4,	/* 4 bits per pixel */
		new int[] { 0x3000*8, 0x2000*8, 0x1000*8, 0x0000*8 }, /* the four bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 },
		new int[] { 0*8, 2*8, 4*8, 6*8, 8*8, 10*8, 12*8, 14*8,
				16*8, 18*8, 20*8, 22*8, 24*8, 26*8, 28*8, 30*8 },
		32*8	/* every sprite takes 32 consecutive bytes */
	);
	
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x0800, charlayout,   0, 4 ),
		new GfxDecodeInfo( REGION_GFX1, 0x0808, charlayout,   0, 4 ),
		new GfxDecodeInfo( REGION_GFX1, 0x0000, spritelayout, 0, 4 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	
	static POKEYinterface pokey_interface = new POKEYinterface
	(
		2,	/* 2 chips */
		1500000,	/* 1.5 MHz??? */
		new int[] { 50, 50 },
		/* The 8 pot handlers */
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		/* The allpot handler */
		new ReadHandlerPtr[] { input_port_4_r, input_port_5_r }
	);
	
	
	static MachineDriver machine_driver_cloud9 = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6502,
				12096000/8, /* 1.512 MHz?? */
				readmem,writemem,null,null,
				interrupt,4
			)
		},
		60, 1460,	/* frames per second, vblank duration??? */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 32*8-1 ),
		gfxdecodeinfo,
		64, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_bitmapped_vh_start,
		generic_bitmapped_vh_stop,
		cloud9_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_POKEY,
				pokey_interface
			)
		},
	
		nvram_handler
	);
	
	
	/***************************************************************************
	
	  Game ROMs
	
	***************************************************************************/
	
	static RomLoadPtr rom_cloud9 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x14000, REGION_CPU1, 0 );/* 64k for code + extra VRAM space */
		ROM_LOAD( "c9_6000.bin", 0x6000, 0x2000, 0xb5d95d98 );
		ROM_LOAD( "c9_8000.bin", 0x8000, 0x2000, 0x49af8f22 );
		ROM_LOAD( "c9_a000.bin", 0xa000, 0x2000, 0x7cf404a6 );
		ROM_LOAD( "c9_c000.bin", 0xc000, 0x2000, 0x26a4d7df );
		ROM_LOAD( "c9_e000.bin", 0xe000, 0x2000, 0x6e663bce );
	
		ROM_REGION( 0x4000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "c9_gfx0.bin", 0x0000, 0x1000, 0xd01a8019 );
		ROM_LOAD( "c9_gfx1.bin", 0x1000, 0x1000, 0x514ac009 );
		ROM_LOAD( "c9_gfx2.bin", 0x2000, 0x1000, 0x930c1ade );
		ROM_LOAD( "c9_gfx3.bin", 0x3000, 0x1000, 0x27e9b88d );
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_cloud9	   = new GameDriver("1983"	,"cloud9"	,"cloud9.java"	,rom_cloud9,null	,machine_driver_cloud9	,input_ports_cloud9	,null	,ROT0	,	"Atari", "Cloud 9 (prototype)", GAME_NO_COCKTAIL );
	
}
