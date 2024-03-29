/***************************************************************************

Super Pac-Man memory map (preliminary)

driver by Aaron Giles

CPU #1:
0000-03ff video RAM
0400-07ff color RAM
0800-0f7f RAM
0f80-0fff sprite data 1 (sprite number & color)
1000-177f RAM
1780-17ff sprite data 2 (x, y position)
1800-1f7f RAM
1f80-1fff sprite data 3 (high bit of y, flip flags, double-size flags)
2000      flip screen
4040-43ff RAM shared with CPU #2
4800-480f custom I/O chip #1
4810-481f custom I/O chip #2
5000      reset CPU #2
5002-5003 IRQ enable
5008-5009 sound enable
500a-500b CPU #2 enable
8000      watchdog timer
c000-ffff ROM

CPU #2:
0000-0040 sound registers
0040-03ff RAM shared with CPU #1
f000-ffff ROM

Interrupts:
CPU #1 IRQ generated by VBLANK
CPU #2 uses no interrupts

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import WIP2.common.ptr.UBytePtr;

import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.cpuintrf.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.vidhrdw.superpac.*;
import static WIP2.mame056.machine.superpac.*;

import static WIP2.arcadeflex056.fileio.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sound.namco.*;
import WIP2.mame056.sound.namcoH.namco_interface;


public class superpac
{
	
	/*************************************
	 *
	 *	Main CPU memory handlers
	 *
	 *************************************/
	
	public static Memory_ReadAddress readmem_cpu1[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x1fff, MRA_RAM ),
		new Memory_ReadAddress( 0x2000, 0x2000, superpac_flipscreen_r ),
		new Memory_ReadAddress( 0x4040, 0x43ff, superpac_sharedram_r ),	/* Pac'n Pal only */
		new Memory_ReadAddress( 0x4800, 0x480f, superpac_customio_1_r ),
		new Memory_ReadAddress( 0x4810, 0x481f, superpac_customio_2_r ),
		new Memory_ReadAddress( 0xa000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_WriteAddress writemem_cpu1[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x03ff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x0400, 0x07ff, colorram_w, colorram ),
		new Memory_WriteAddress( 0x0800, 0x0f7f, MWA_RAM ),
		new Memory_WriteAddress( 0x0f80, 0x0fff, MWA_RAM, spriteram, spriteram_size ),
		new Memory_WriteAddress( 0x1000, 0x177f, MWA_RAM ),
		new Memory_WriteAddress( 0x1780, 0x17ff, MWA_RAM, spriteram_2 ),
		new Memory_WriteAddress( 0x1800, 0x1f7f, MWA_RAM ),
		new Memory_WriteAddress( 0x1f80, 0x1fff, MWA_RAM, spriteram_3 ),
		new Memory_WriteAddress( 0x2000, 0x2000, superpac_flipscreen_w ),
		new Memory_WriteAddress( 0x4040, 0x43ff, superpac_sharedram_w, superpac_sharedram ),
		new Memory_WriteAddress( 0x4800, 0x480f, MWA_RAM, superpac_customio_1 ),
		new Memory_WriteAddress( 0x4810, 0x481f, MWA_RAM, superpac_customio_2 ),
		new Memory_WriteAddress( 0x5000, 0x5000, superpac_reset_2_w ),
		new Memory_WriteAddress( 0x5002, 0x5003, superpac_interrupt_enable_w ),
		new Memory_WriteAddress( 0x5008, 0x5009, mappy_sound_enable_w ),
		new Memory_WriteAddress( 0x500a, 0x500b, superpac_cpu_enable_w ),
		new Memory_WriteAddress( 0x8000, 0x8000, watchdog_reset_w ),
		new Memory_WriteAddress( 0xa000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	
	/*************************************
	 *
	 *	Sound CPU memory handlers
	 *
	 *************************************/
	
	public static Memory_ReadAddress readmem_cpu2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0040, 0x03ff, superpac_sharedram_r ),
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_WriteAddress writemem_cpu2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x003f, mappy_sound_w, namco_soundregs ),
		new Memory_WriteAddress( 0x0040, 0x03ff, superpac_sharedram_w ),
		new Memory_WriteAddress( 0x2000, 0x2001, superpac_interrupt_enable_w ),
		new Memory_WriteAddress( 0x2006, 0x2007, mappy_sound_enable_w ),
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	
	/*************************************
	 *
	 *	Input ports
	 *
	 *************************************/
	
	static InputPortPtr input_ports_superpac = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* DSW0 */
		PORT_DIPNAME( 0x0f, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "Rank 0-Normal" );
		PORT_DIPSETTING(    0x01, "Rank 1-Easiest" );
		PORT_DIPSETTING(    0x02, "Rank 2" );
		PORT_DIPSETTING(    0x03, "Rank 3" );
		PORT_DIPSETTING(    0x04, "Rank 4" );
		PORT_DIPSETTING(    0x05, "Rank 5" );
		PORT_DIPSETTING(    0x06, "Rank 6-Medium" );
		PORT_DIPSETTING(    0x07, "Rank 7" );
		PORT_DIPSETTING(    0x08, "Rank 8-Default" );
		PORT_DIPSETTING(    0x09, "Rank 9" );
		PORT_DIPSETTING(    0x0a, "Rank A" );
		PORT_DIPSETTING(    0x0b, "Rank B-Hardest" );
		PORT_DIPSETTING(    0x0c, "Rank C-Easy Auto" );
		PORT_DIPSETTING(    0x0d, "Rank D-Auto" );
		PORT_DIPSETTING(    0x0e, "Rank E-Auto" );
		PORT_DIPSETTING(    0x0f, "Rank F-Hard Auto" );
		PORT_DIPNAME( 0x30, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x20, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x07, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x07, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_7C") );
		PORT_DIPNAME( 0x38, 0x00, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x38, "None" );
		PORT_DIPSETTING(    0x30, "30k" );
		PORT_DIPSETTING(    0x08, "30k 80k" );
		PORT_DIPSETTING(    0x00, "30k 100k" );
		PORT_DIPSETTING(    0x10, "30k 120k" );
		PORT_DIPSETTING(    0x18, "30k 80k 80k" );
		PORT_DIPSETTING(    0x20, "30k 100k 100k" );
		PORT_DIPSETTING(    0x28, "30k 120k 120k" );
	/* TODO: bonus scores for 5 lives */
	/* 	PORT_DIPNAME( 0x38, 0x00, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x38, "None" );
		PORT_DIPSETTING(    0x28, "30k" );
		PORT_DIPSETTING(    0x30, "40k" );
		PORT_DIPSETTING(    0x00, "30k 100k" );
		PORT_DIPSETTING(    0x08, "30k 120k" );
		PORT_DIPSETTING(    0x10, "40k 120k" );
		PORT_DIPSETTING(    0x18, "30k 100k 100k" );
		PORT_DIPSETTING(    0x20, "40k 120k 120k" );*/
		PORT_DIPNAME( 0xc0, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x40, "1" );
		PORT_DIPSETTING(    0x80, "2" );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPSETTING(    0xc0, "5" );
	
		PORT_START(); 	/* FAKE */
		/* The player inputs are not memory mapped, they are handled by an I/O chip. */
		/* These fake input ports are read by mappy_customio_data_r() */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1, 1 );
		PORT_BITX(0x20, IP_ACTIVE_HIGH, IPT_BUTTON1, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );
		PORT_BIT( 0xc0, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* FAKE */
		PORT_BIT_IMPULSE( 0x01, IP_ACTIVE_HIGH, IPT_COIN1, 1 );
		PORT_BIT_IMPULSE( 0x02, IP_ACTIVE_HIGH, IPT_COIN2, 1 );
		PORT_BIT( 0x0c, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_HIGH, IPT_START1, 1 );
		PORT_BIT_IMPULSE( 0x20, IP_ACTIVE_HIGH, IPT_START2, 1 );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START(); 	/* FAKE */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_COCKTAIL, 1 );
		PORT_BITX(0x20, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_COCKTAIL, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );
		PORT_BIT( 0xc0, IP_ACTIVE_HIGH, IPT_UNUSED );
	INPUT_PORTS_END(); }}; 
	
	
	static InputPortPtr input_ports_pacnpal = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* DSW0 */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x0c, 0x00, "Rank" );
		PORT_DIPSETTING(    0x00, "A" );
		PORT_DIPSETTING(    0x04, "B" );
		PORT_DIPSETTING(    0x08, "C" );
		PORT_DIPSETTING(    0x0c, "D" );
		PORT_BIT( 0xf0, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x07, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x07, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_7C") );
		PORT_DIPNAME( 0x38, 0x18, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "None" );
		PORT_DIPSETTING(    0x38, "30k" );
		PORT_DIPSETTING(    0x18, "20k 70k" );
		PORT_DIPSETTING(    0x20, "30k 70k" );
		PORT_DIPSETTING(    0x28, "30k 80k" );
		PORT_DIPSETTING(    0x30, "30k 100k" );
		PORT_DIPSETTING(    0x08, "20k 70k 70k" );
		PORT_DIPSETTING(    0x10, "30k 80k 80k" );
		/* TODO: bonus scores are different for 5 lives */
	/* 	PORT_DIPNAME( 0x38, 0x18, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "None" );
		PORT_DIPSETTING(    0x30, "30k" );
		PORT_DIPSETTING(    0x38, "40k" );
		PORT_DIPSETTING(    0x18, "30k 80k" );
		PORT_DIPSETTING(    0x20, "30k 100k" );
		PORT_DIPSETTING(    0x28, "40k 120k" );
		PORT_DIPSETTING(    0x08, "30k 80k 80k" );
		PORT_DIPSETTING(    0x10, "40k 100k 100k" );*/
		PORT_DIPNAME( 0xc0, 0x80, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "1" );
		PORT_DIPSETTING(    0x40, "2" );
		PORT_DIPSETTING(    0x80, "3" );
		PORT_DIPSETTING(    0xc0, "5" );
	
		PORT_START(); 	/* FAKE */
		/* The player inputs are not memory mapped, they are handled by an I/O chip. */
		/* These fake input ports are read by mappy_customio_data_r() */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1, 2 );
		PORT_BIT_IMPULSE( 0x20, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_COCKTAIL, 2 );
		PORT_BIT( 0xc0, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* FAKE */
		PORT_BIT_IMPULSE( 0x01, IP_ACTIVE_HIGH, IPT_COIN1, 2 );
		PORT_BIT_IMPULSE( 0x02, IP_ACTIVE_HIGH, IPT_COIN2, 2 );
		PORT_BIT( 0x0c, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_HIGH, IPT_START1, 2 );
		PORT_BIT_IMPULSE( 0x20, IP_ACTIVE_HIGH, IPT_START2, 2 );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START(); 	/* FAKE */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0xf0, IP_ACTIVE_HIGH, IPT_UNUSED );
	INPUT_PORTS_END(); }}; 
	
	
	
	/*************************************
	 *
	 *	Graphics layouts
	 *
	 *************************************/
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,
		RGN_FRAC(1,1),
		2,
		new int[] { 0, 4 },
		new int[] { 8*8+0, 8*8+1, 8*8+2, 8*8+3, 0, 1, 2, 3 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		16*8
	);
	
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,
		RGN_FRAC(1,1),
		2,
		new int[] { 0, 4 },
		new int[] { 0, 1, 2, 3, 8*8, 8*8+1, 8*8+2, 8*8+3,
				16*8+0, 16*8+1, 16*8+2, 16*8+3, 24*8+0, 24*8+1, 24*8+2, 24*8+3 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				32*8, 33*8, 34*8, 35*8, 36*8, 37*8, 38*8, 39*8 },
		64*8
	);
	
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,      0, 64 ),
		new GfxDecodeInfo( REGION_GFX2, 0, spritelayout, 64*4, 64 ),
		new GfxDecodeInfo( -1 )
	};
	
	
	
	/*************************************
	 *
	 *	Sound interfaces
	 *
	 *************************************/
	
	static namco_interface namco_interface = new namco_interface
	(
		24000,	/* sample rate */
		8,		/* number of voices */
		100,	/* playback volume */
		REGION_SOUND1	/* memory region */
        );
	
	
	
	/*************************************
	 *
	 *	Machine driver
	 *
	 *************************************/
	
	static MachineDriver machine_driver_superpac = new MachineDriver
	(
		/* basic machine hardware  */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6809,
				1100000,
				readmem_cpu1,writemem_cpu1,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_M6809,
				1100000,
				readmem_cpu2,writemem_cpu2,null,null,
				interrupt,1
			)
		},
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,
		100,
		superpac_init_machine,
	
		/* video hardware */
		36*8, 28*8,	new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),
		gfxdecodeinfo,
		32,	4*(64+64),
		superpac_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_vh_start,
		generic_vh_stop,
		superpac_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_NAMCO,
				namco_interface
			)
		}
	);
	
	
	
	/*************************************
	 *
	 *	ROM definitions
	 *
	 *************************************/
	
	static RomLoadPtr rom_superpac = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "sp1.2",        0xc000, 0x2000, 0x4bb33d9c );
		ROM_LOAD( "sp1.1",        0xe000, 0x2000, 0x846fbb4a );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "spc-3.1k",     0xf000, 0x1000, 0x04445ddb );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "sp1.6",        0x0000, 0x1000, 0x91c5935c );
	
		ROM_REGION( 0x2000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "spv-2.3f",     0x0000, 0x2000, 0x670a42f2 );
	
		ROM_REGION( 0x0220, REGION_PROMS, 0 );
		ROM_LOAD( "superpac.4c",  0x0000, 0x0020, 0x9ce22c46 );/* palette */
		ROM_LOAD( "superpac.4e",  0x0020, 0x0100, 0x1253c5c1 );/* chars */
		ROM_LOAD( "superpac.3l",  0x0120, 0x0100, 0xd4d7026f );/* sprites */
	
		ROM_REGION( 0x0100, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "superpac.3m",  0x0000, 0x0100, 0xad43688f );
	ROM_END(); }}; 
	
	
	static RomLoadPtr rom_superpcm = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "spc-2.1c",     0xc000, 0x2000, 0x1a38c30e );
		ROM_LOAD( "spc-1.1b",     0xe000, 0x2000, 0x730e95a9 );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "spc-3.1k",     0xf000, 0x1000, 0x04445ddb );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "spv-1.3c",     0x0000, 0x1000, 0x78337e74 );
	
		ROM_REGION( 0x2000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "spv-2.3f",     0x0000, 0x2000, 0x670a42f2 );
	
		ROM_REGION( 0x0220, REGION_PROMS, 0 );
		ROM_LOAD( "superpac.4c",  0x0000, 0x0020, 0x9ce22c46 );/* palette */
		ROM_LOAD( "superpac.4e",  0x0020, 0x0100, 0x1253c5c1 );/* chars */
		ROM_LOAD( "superpac.3l",  0x0120, 0x0100, 0xd4d7026f );/* sprites */
	
		ROM_REGION( 0x0100, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "superpac.3m",  0x0000, 0x0100, 0xad43688f );
	ROM_END(); }}; 
	
	
	static RomLoadPtr rom_pacnpal = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "pap13b.cpu",   0xa000, 0x2000, 0xed64a565 );
		ROM_LOAD( "pap12b.cpu",   0xc000, 0x2000, 0x15308bcf );
		ROM_LOAD( "pap11b.cpu",   0xe000, 0x2000, 0x3cac401c );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "pap14.cpu",    0xf000, 0x1000, 0x330e20de );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "pap16.cpu",    0x0000, 0x1000, 0xa36b96cb );
	
		ROM_REGION( 0x2000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "pap15.vid",    0x0000, 0x2000, 0xfb6f56e3 );
	
		ROM_REGION( 0x0220, REGION_PROMS, 0 );
		ROM_LOAD( "papi6.vid",    0x0000, 0x0020, 0x52634b41 );/* palette */
		ROM_LOAD( "papi5.vid",    0x0020, 0x0100, 0xac46203c );/* chars */
		ROM_LOAD( "papi4.vid",    0x0120, 0x0100, 0x686bde84 );/* sprites */
	
		ROM_REGION( 0x0100, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "papi3.cpu",    0x0000, 0x0100, 0x83c31a98 );
	ROM_END(); }}; 
	
	
	static RomLoadPtr rom_pacnpal2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "pap1_3.1d",    0xa000, 0x2000, 0xd7ec2719 );
		ROM_LOAD( "pap1_2.1c",    0xc000, 0x2000, 0x0245396e );
		ROM_LOAD( "pap1_1.1b",    0xe000, 0x2000, 0x7f046b58 );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "pap14.cpu",    0xf000, 0x1000, 0x330e20de );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "pap16.cpu",    0x0000, 0x1000, 0xa36b96cb );
	
		ROM_REGION( 0x2000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "pap15.vid",    0x0000, 0x2000, 0xfb6f56e3 );
	
		ROM_REGION( 0x0220, REGION_PROMS, 0 );
		ROM_LOAD( "papi6.vid",    0x0000, 0x0020, 0x52634b41 );/* palette */
		ROM_LOAD( "papi5.vid",    0x0020, 0x0100, 0xac46203c );/* chars */
		ROM_LOAD( "papi4.vid",    0x0120, 0x0100, 0x686bde84 );/* sprites */
	
		ROM_REGION( 0x0100, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "papi3.cpu",    0x0000, 0x0100, 0x83c31a98 );
	ROM_END(); }}; 
	
	
	static RomLoadPtr rom_pacnchmp = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "pap3.1d",      0xa000, 0x2000, 0x20a07d3d );
		ROM_LOAD( "pap3.1c",      0xc000, 0x2000, 0x505bae56 );
		ROM_LOAD( "pap11b.cpu",   0xe000, 0x2000, 0x3cac401c );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "pap14.cpu",    0xf000, 0x1000, 0x330e20de );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "pap2.3c",      0x0000, 0x1000, 0x93d15c30 );
	
		ROM_REGION( 0x2000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "pap2.3f",      0x0000, 0x2000, 0x39f44aa4 );
	
		ROM_REGION( 0x0220, REGION_PROMS, 0 );
		ROM_LOAD( "papi6.vid",    0x0000, 0x0020, BADCRC( 0x52634b41 )); /* palette */
		ROM_LOAD( "papi5.vid",    0x0020, 0x0100, BADCRC( 0xac46203c )); /* chars */
		ROM_LOAD( "papi4.vid",    0x0120, 0x0100, BADCRC( 0x686bde84 )); /* sprites */
	
		ROM_REGION( 0x0100, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "papi3.cpu",    0x0000, 0x0100, 0x83c31a98 );
	ROM_END(); }}; 
	
	
	
	/*************************************
	 *
	 *	Game drivers
	 *
	 *************************************/
	
	public static GameDriver driver_superpac	   = new GameDriver("1982"	,"superpac"	,"superpac.java"	,rom_superpac,null	,machine_driver_superpac	,input_ports_superpac	,null	,ROT90	,	"Namco", "Super Pac-Man" );
	public static GameDriver driver_superpcm	   = new GameDriver("1982"	,"superpcm"	,"superpac.java"	,rom_superpcm,driver_superpac	,machine_driver_superpac	,input_ports_superpac	,null	,ROT90	,	"[Namco] (Bally Midway license)", "Super Pac-Man (Midway)" );
	public static GameDriver driver_pacnpal	   = new GameDriver("1983"	,"pacnpal"	,"superpac.java"	,rom_pacnpal,null	,machine_driver_superpac	,input_ports_pacnpal	,null	,ROT90	,	"Namco", "Pac & Pal" );
	public static GameDriver driver_pacnpal2	   = new GameDriver("1983"	,"pacnpal2"	,"superpac.java"	,rom_pacnpal2,driver_pacnpal	,machine_driver_superpac	,input_ports_pacnpal	,null	,ROT90	,	"Namco", "Pac & Pal (older)" );
	public static GameDriver driver_pacnchmp	   = new GameDriver("1983"	,"pacnchmp"	,"superpac.java"	,rom_pacnchmp,driver_pacnpal	,machine_driver_superpac	,input_ports_pacnpal	,null	,ROT90	,	"Namco", "Pac-Man & Chomp Chomp", GAME_IMPERFECT_COLORS );
}
