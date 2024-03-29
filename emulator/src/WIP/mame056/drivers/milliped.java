/***************************************************************************

Millipede memory map (preliminary)

driver by Ivan Mackintosh

0400-040F		POKEY 1
0800-080F		POKEY 2
1000-13BF		SCREEN RAM (8x8 TILES, 32x30 SCREEN)
13C0-13CF		SPRITE IMAGE OFFSETS
13D0-13DF		SPRITE HORIZONTAL OFFSETS
13E0-13EF		SPRITE VERTICAL OFFSETS
13F0-13FF		SPRITE COLOR OFFSETS

2000			BIT 1-4 trackball
				BIT 5 IS P1 FIRE
				BIT 6 IS P1 START
				BIT 7 IS VBLANK

2001			BIT 1-4 trackball
				BIT 5 IS P2 FIRE
				BIT 6 IS P2 START
				BIT 7,8 (?)

2010			BIT 1 IS P1 RIGHT
				BIT 2 IS P1 LEFT
				BIT 3 IS P1 DOWN
				BIT 4 IS P1 UP
				BIT 5 IS SLAM, LEFT COIN, AND UTIL COIN
				BIT 6,7 (?)
				BIT 8 IS RIGHT COIN
2030			earom read
2480-249F		COLOR RAM
2500-2502		Coin counters
2503-2504		LEDs
2505-2507		Coin door lights ??
2600			INTERRUPT ACKNOWLEDGE
2680			CLEAR WATCHDOG
2700			earom control
2780			earom write
4000-7FFF		GAME CODE

*************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.vidhrdw.milliped.*;

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

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.machine.atari_vg.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sound.pokey.*;
import static WIP2.mame056.sound.pokeyH.*;

public class milliped
{
	
	
	
	/*
	 * This wrapper routine is necessary because Millipede requires a direction bit
	 * to be set or cleared. The direction bit is held until the mouse is moved
	 * again. We still don't understand why the difference between
	 * two consecutive reads must not exceed 7. After all, the input is 4 bits
	 * wide, and we have a fifth bit for the sign...
	 *
	 * The other reason it is necessary is that Millipede uses the same address to
	 * read the dipswitches.
	 */
	
	static int dsw_select;
	
	public static WriteHandlerPtr milliped_input_select_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		dsw_select = (data == 0)?1:0;
	} };
        
        static int oldpos,sign;
	
	public static ReadHandlerPtr milliped_IN0_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		
		int newpos;
	
		if (dsw_select != 0)
			return (readinputport(0) | sign);
	
		newpos = readinputport(6);
		if (newpos != oldpos)
		{
			sign = (newpos - oldpos) & 0x80;
			oldpos = newpos;
		}
	
		return ((readinputport(0) & 0x70) | (oldpos & 0x0f) | sign );
	} };
	
	public static ReadHandlerPtr milliped_IN1_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		
		int newpos;
	
		if (dsw_select != 0)
			return (readinputport(1) | sign);
	
		newpos = readinputport(7);
		if (newpos != oldpos)
		{
			sign = (newpos - oldpos) & 0x80;
			oldpos = newpos;
		}
	
		return ((readinputport(1) & 0x70) | (oldpos & 0x0f) | sign );
	} };
	
	public static WriteHandlerPtr milliped_led_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/*TODO*///set_led_status(offset,~data & 0x80);
	} };
	
	public static WriteHandlerPtr milliped_coin_counter_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_counter_w.handler(offset,data);
	} };
	
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x03ff, MRA_RAM ),
		new Memory_ReadAddress( 0x0400, 0x040f, pokey1_r ),
		new Memory_ReadAddress( 0x0800, 0x080f, pokey2_r ),
		new Memory_ReadAddress( 0x1000, 0x13ff, MRA_RAM ),
		new Memory_ReadAddress( 0x2000, 0x2000, milliped_IN0_r ),
		new Memory_ReadAddress( 0x2001, 0x2001, milliped_IN1_r ),
		new Memory_ReadAddress( 0x2010, 0x2010, input_port_2_r ),
		new Memory_ReadAddress( 0x2011, 0x2011, input_port_3_r ),
		new Memory_ReadAddress( 0x2030, 0x2030, atari_vg_earom_r ),
		new Memory_ReadAddress( 0x4000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),		/* for the reset / interrupt vectors */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x03ff, MWA_RAM ),
		new Memory_WriteAddress( 0x0400, 0x040f, pokey1_w ),
		new Memory_WriteAddress( 0x0800, 0x080f, pokey2_w ),
		new Memory_WriteAddress( 0x1000, 0x13ff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x13c0, 0x13ff, MWA_RAM, spriteram ),
		new Memory_WriteAddress( 0x2480, 0x249f, milliped_paletteram_w, paletteram ),
		new Memory_WriteAddress( 0x2500, 0x2502, milliped_coin_counter_w ),
		new Memory_WriteAddress( 0x2503, 0x2504, milliped_led_w ),
		new Memory_WriteAddress( 0x2505, 0x2505, milliped_input_select_w ),
	//	new Memory_WriteAddress( 0x2506, 0x2507, MWA_NOP ), /* ? */
		new Memory_WriteAddress( 0x2600, 0x2600, MWA_NOP ), /* IRQ ack */
		new Memory_WriteAddress( 0x2680, 0x2680, watchdog_reset_w ),
		new Memory_WriteAddress( 0x2700, 0x2700, atari_vg_earom_ctrl_w ),
		new Memory_WriteAddress( 0x2780, 0x27bf, atari_vg_earom_w ),
		new Memory_WriteAddress( 0x4000, 0x73ff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	static InputPortPtr input_ports_milliped = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* IN0 $2000 */ /* see port 6 for x trackball */
		PORT_DIPNAME(0x03, 0x00, "Language" );
		PORT_DIPSETTING(   0x00, "English" );
		PORT_DIPSETTING(   0x01, "German" );
		PORT_DIPSETTING(   0x02, "French" );
		PORT_DIPSETTING(   0x03, "Spanish" );
		PORT_DIPNAME(0x0c, 0x04, "Bonus" );
		PORT_DIPSETTING(   0x00, "0" );
		PORT_DIPSETTING(   0x04, "0 1x" );
		PORT_DIPSETTING(   0x08, "0 1x 2x" );
		PORT_DIPSETTING(   0x0c, "0 1x 2x 3x" );
		PORT_BIT ( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT ( 0x20, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT ( 0x40, IP_ACTIVE_HIGH, IPT_VBLANK );
		PORT_BIT ( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );/* trackball sign bit */
	
		PORT_START(); 	/* IN1 $2001 */ /* see port 7 for y trackball */
		PORT_DIPNAME(0x01, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(   0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(   0x01, DEF_STR( "On") );
		PORT_DIPNAME(0x02, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(   0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(   0x02, DEF_STR( "On") );
		PORT_DIPNAME(0x04, 0x00, "Credit Minimum" );
		PORT_DIPSETTING(   0x00, "1" );
		PORT_DIPSETTING(   0x04, "2" );
		PORT_DIPNAME(0x08, 0x00, "Coin Counters" );
		PORT_DIPSETTING(   0x00, "1" );
		PORT_DIPSETTING(   0x08, "2" );
		PORT_BIT ( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT ( 0x20, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT ( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT ( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );/* trackball sign bit */
	
		PORT_START(); 	/* IN2 $2010 */
		PORT_BIT ( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT ( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT ( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT ( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT ( 0x10, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT ( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT ( 0x40, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT ( 0x80, IP_ACTIVE_LOW, IPT_COIN3 );
	
		PORT_START(); 	/* IN3 $2011 */
		PORT_BIT ( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT ( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT ( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT ( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT ( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT ( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT ( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_SERVICE( 0x80, IP_ACTIVE_LOW );
	
		PORT_START(); 	/* 4 */ /* DSW1 $0408 */
		PORT_DIPNAME(0x01, 0x00, "Millipede Head" );
		PORT_DIPSETTING(   0x00, "Easy" );
		PORT_DIPSETTING(   0x01, "Hard" );
		PORT_DIPNAME(0x02, 0x00, "Beetle" );
		PORT_DIPSETTING(   0x00, "Easy" );
		PORT_DIPSETTING(   0x02, "Hard" );
		PORT_DIPNAME(0x0c, 0x04, DEF_STR( "Lives") );
		PORT_DIPSETTING(   0x00, "2" );
		PORT_DIPSETTING(   0x04, "3" );
		PORT_DIPSETTING(   0x08, "4" );
		PORT_DIPSETTING(   0x0c, "5" );
		PORT_DIPNAME(0x30, 0x10, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(   0x00, "12000" );
		PORT_DIPSETTING(   0x10, "15000" );
		PORT_DIPSETTING(   0x20, "20000" );
		PORT_DIPSETTING(   0x30, "None" );
		PORT_DIPNAME(0x40, 0x00, "Spider" );
		PORT_DIPSETTING(   0x00, "Easy" );
		PORT_DIPSETTING(   0x40, "Hard" );
		PORT_DIPNAME(0x80, 0x00, "Starting Score Select" );
		PORT_DIPSETTING(   0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(   0x00, DEF_STR( "On") );
	
		PORT_START(); 	/* 5 */ /* DSW2 $0808 */
		PORT_DIPNAME(0x03, 0x02, DEF_STR( "Coinage") );
		PORT_DIPSETTING(   0x03, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(   0x02, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(   0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(   0x00, DEF_STR( "Free_Play") );
		PORT_DIPNAME(0x0c, 0x00, "Right Coin" );
		PORT_DIPSETTING(   0x00, "*1" );
		PORT_DIPSETTING(   0x04, "*4" );
		PORT_DIPSETTING(   0x08, "*5" );
		PORT_DIPSETTING(   0x0c, "*6" );
		PORT_DIPNAME(0x10, 0x00, "Left Coin" );
		PORT_DIPSETTING(   0x00, "*1" );
		PORT_DIPSETTING(   0x10, "*2" );
		PORT_DIPNAME(0xe0, 0x00, "Bonus Coins" );
		PORT_DIPSETTING(   0x00, "None" );
		PORT_DIPSETTING(   0x20, "3 credits/2 coins" );
		PORT_DIPSETTING(   0x40, "5 credits/4 coins" );
		PORT_DIPSETTING(   0x60, "6 credits/4 coins" );
		PORT_DIPSETTING(   0x80, "6 credits/5 coins" );
		PORT_DIPSETTING(   0xa0, "4 credits/3 coins" );
		PORT_DIPSETTING(   0xc0, "Demo mode" );
	
		PORT_START(); 	/* IN6: FAKE - used for trackball-x at $2000 */
		PORT_ANALOGX( 0xff, 0x00, IPT_TRACKBALL_X | IPF_REVERSE, 50, 10, 0, 0, IP_KEY_NONE, IP_KEY_NONE, IP_JOY_NONE, IP_JOY_NONE );
	
		PORT_START(); 	/* IN7: FAKE - used for trackball-y at $2001 */
		PORT_ANALOGX( 0xff, 0x00, IPT_TRACKBALL_Y, 50, 10, 0, 0, IP_KEY_NONE, IP_KEY_NONE, IP_JOY_NONE, IP_JOY_NONE );
	INPUT_PORTS_END(); }}; 
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		256,	/* 256 characters */
		2,	/* 2 bits per pixel */
		new int[] { 0, 256*8*8 }, /* the two bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8 /* every char takes 8 consecutive bytes */
	);
	static GfxLayout spritelayout = new GfxLayout
	(
		8,16,	/* 16*8 sprites */
		128,	/* 64 sprites */
		2,	/* 2 bits per pixel */
		new int[] { 0, 128*16*8 },	/* the two bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8 },
		16*8	/* every sprite takes 16 consecutive bytes */
	);
	
	
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,     0, 4 ),
		new GfxDecodeInfo( REGION_GFX1, 0, spritelayout, 4*4, 4*4*4*4 ),
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
	
	
	
	static MachineDriver machine_driver_milliped = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6502,
				1500000,	/* 1.5 MHz ???? */
				readmem,writemem,null,null,
				interrupt,4
			)
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		10,
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 0*8, 30*8-1 ),
		gfxdecodeinfo,
		32, 4*4+4*4*4*4*4,
		milliped_init_palette,
	
		VIDEO_TYPE_RASTER|VIDEO_SUPPORTS_DIRTY,
		null,
		generic_vh_start,
		generic_vh_stop,
		milliped_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_POKEY,
				pokey_interface
			)
		},
	
		atari_vg_earom_handler
	);
	
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_milliped = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "milliped.104", 0x4000, 0x1000, 0x40711675 );
		ROM_LOAD( "milliped.103", 0x5000, 0x1000, 0xfb01baf2 );
		ROM_LOAD( "milliped.102", 0x6000, 0x1000, 0x62e137e0 );
		ROM_LOAD( "milliped.101", 0x7000, 0x1000, 0x46752c7d );
		ROM_RELOAD( 			  0xf000, 0x1000 );/* for the reset and interrupt vectors */
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "milliped.106", 0x0000, 0x0800, 0xf4468045 );
		ROM_LOAD( "milliped.107", 0x0800, 0x0800, 0x68c3437a );
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_milliped	   = new GameDriver("1982"	,"milliped"	,"milliped.java"	,rom_milliped,null	,machine_driver_milliped	,input_ports_milliped	,null	,ROT270	,	"Atari", "Millipede" );
	
}
