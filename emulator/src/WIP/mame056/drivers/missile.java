/***************************************************************************


	  Modified from original schematics...

	  MISSILE COMMAND
	  ---------------
	  HEX		 R/W   D7 D6 D5 D4 D3 D2 D2 D0	function
	  ---------+-----+------------------------+------------------------
	  0000-01FF  R/W   D  D  D	D  D  D  D	D	512 bytes working ram

	  0200-05FF  R/W   D  D  D	D  D  D  D	D	3rd color bit region
												of screen ram.
												Each bit of every odd byte is the low color
												bit for the bottom scanlines
												The schematics say that its for the bottom
												32 scanlines, although the code only accesses
												$401-$5FF for the bottom 8 scanlines...
												Pretty wild, huh?

	  0600-063F  R/W   D  D  D	D  D  D  D	D	More working ram.

	  0640-3FFF  R/W   D  D  D	D  D  D  D	D	2-color bit region of
												screen ram.
												Writes to 4 bytes each to effectively
												address $1900-$ffff.

	  1900-FFFF  R/W   D  D 					2-color bit region of
												screen ram
													  Only accessed with
													   LDA ($ZZ,X) and
													   STA ($ZZ,X)
													  Those instructions take longer
													  than 5 cycles.

	  ---------+-----+------------------------+------------------------
	  4000-400F  R/W   D  D  D	D  D  D  D	D	POKEY ports.
	  -----------------------------------------------------------------
	  4008		 R	   D  D  D	D  D  D  D	D	Game Option switches
	  -----------------------------------------------------------------
	  4800		 R	   D						Right coin
	  4800		 R		  D 					Center coin
	  4800		 R			 D					Left coin
	  4800		 R				D				1 player start
	  4800		 R				   D			2 player start
	  4800		 R					  D 		2nd player left fire(cocktail)
	  4800		 R						 D		2nd player center fire	"
	  4800		 R							D	2nd player right fire	"
	  ---------+-----+------------------------+------------------------
	  4800		 R				   D  D  D	D	Horiz trackball displacement
															if ctrld=high.
	  4800		 R	   D  D  D	D				Vert trackball displacement
															if ctrld=high.
	  ---------+-----+------------------------+------------------------
	  4800		 W	   D						Unused ??
	  4800		 W		  D 					screen flip
	  4800		 W			 D					left coin counter
	  4800		 W				D				center coin counter
	  4800		 W				   D			right coin counter
	  4800		 W					  D 		2 player start LED.
	  4800		 W						 D		1 player start LED.
	  4800		 W							D	CTRLD, 0=read switches,
															1= read trackball.
	  ---------+-----+------------------------+------------------------
	  4900		 R	   D						VBLANK read
	  4900		 R		  D 					Self test switch input.
	  4900		 R			 D					SLAM switch input.
	  4900		 R				D				Horiz trackball direction input.
	  4900		 R				   D			Vert trackball direction input.
	  4900		 R					  D 		1st player left fire.
	  4900		 R						 D		1st player center fire.
	  4900		 R							D	1st player right fire.
	  ---------+-----+------------------------+------------------------
	  4A00		 R	   D  D  D	D  D  D  D	D	Pricing Option switches.
	  4B00-4B07  W				   D  D  D	D	Color RAM.
	  4C00		 W								Watchdog.
	  4D00		 W								Interrupt acknowledge.
	  ---------+-----+------------------------+------------------------
	  5000-7FFF  R	   D  D  D	D  D  D  D	D	Program.
	  ---------+-----+------------------------+------------------------




MISSILE COMMAND SWITCH SETTINGS (Atari, 1980)
---------------------------------------------


GAME OPTIONS:
(8-position switch at R8)

1	2	3	4	5	6	7	8	Meaning
-------------------------------------------------------------------------
Off Off 						Game starts with 7 cities
On	On							Game starts with 6 cities
On	Off 						Game starts with 5 cities
Off On							Game starts with 4 cities
		On						No bonus credit
		Off 					1 bonus credit for 4 successive coins
			On					Large trak-ball input
			Off 				Mini Trak-ball input
				On	Off Off 	Bonus city every  8000 pts
				On	On	On		Bonus city every 10000 pts
				Off On	On		Bonus city every 12000 pts
				On	Off On		Bonus city every 14000 pts
				Off Off On		Bonus city every 15000 pts
				On	On	Off 	Bonus city every 18000 pts
				Off On	Off 	Bonus city every 20000 pts
				Off Off Off 	No bonus cities
							On	Upright
							Off Cocktail



PRICING OPTIONS:
(8-position switch at R10)

1	2	3	4	5	6	7	8	Meaning
-------------------------------------------------------------------------
On	On							1 coin 1 play
Off On							Free play
On Off							2 coins 1 play
Off Off 						1 coin 2 plays
		On	On					Right coin mech * 1
		Off On					Right coin mech * 4
		On	Off 				Right coin mech * 5
		Off Off 				Right coin mech * 6
				On				Center coin mech * 1
				Off 			Center coin mech * 2
					On	On		English
					Off On		French
					On	Off 	German
					Off Off 	Spanish
							On	( Unused )
							Off ( Unused )


******************************************************************************************/
/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.vidhrdw.missile.*;
import static WIP.mame056.machine.missile.*;

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
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sound.pokeyH.*;
import static WIP2.mame056.sound.pokey.*;

public class missile
{
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x18ff, MRA_RAM ),
		new Memory_ReadAddress( 0x1900, 0xfff9, missile_r ), /* shared region */
		new Memory_ReadAddress( 0xfffa, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x03ff, MWA_RAM ),
		new Memory_WriteAddress( 0x0400, 0x05ff, missile_video_3rd_bit_w ),
		new Memory_WriteAddress( 0x0600, 0x063f, MWA_RAM ),
		new Memory_WriteAddress( 0x0640, 0x4fff, missile_w ), /* shared region */
		new Memory_WriteAddress( 0x5000, 0xffff, missile_video2_w, missile_video2ram ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	
	static InputPortPtr input_ports_missile = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON3 | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN3 );
	
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON3 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x18, 0x00, IPT_UNUSED );/* trackball input, handled in machine/missile.c */
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BITX(0x40, IP_ACTIVE_LOW, IPT_SERVICE , DEF_STR( "Service_Mode"), KEYCODE_F2, IP_JOY_NONE );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_VBLANK );
	
		PORT_START(); 	/* IN2 */
		PORT_DIPNAME(0x03, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(   0x01, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(   0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(   0x03, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(   0x02, DEF_STR( "Free_Play") );
		PORT_DIPNAME(0x0c, 0x00, "Right Coin" );
		PORT_DIPSETTING(   0x00, "*1" );
		PORT_DIPSETTING(   0x04, "*4" );
		PORT_DIPSETTING(   0x08, "*5" );
		PORT_DIPSETTING(   0x0c, "*6" );
		PORT_DIPNAME(0x10, 0x00, "Center Coin" );
		PORT_DIPSETTING(   0x00, "*1" );
		PORT_DIPSETTING(   0x10, "*2" );
		PORT_DIPNAME(0x60, 0x00, "Language" );
		PORT_DIPSETTING(   0x00, "English" );
		PORT_DIPSETTING(   0x20, "French" );
		PORT_DIPSETTING(   0x40, "German" );
		PORT_DIPSETTING(   0x60, "Spanish" );
		PORT_DIPNAME(0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(   0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(   0x00, DEF_STR( "On") );
	
		PORT_START(); 	/* IN3 */
		PORT_DIPNAME(0x03, 0x00, "Cities" );
		PORT_DIPSETTING(   0x02, "4" );
		PORT_DIPSETTING(   0x01, "5" );
		PORT_DIPSETTING(   0x03, "6" );
		PORT_DIPSETTING(   0x00, "7" );
		PORT_DIPNAME(0x04, 0x04, "Bonus Credit for 4 Coins" );
		PORT_DIPSETTING(   0x04, DEF_STR( "No") );
		PORT_DIPSETTING(   0x00, DEF_STR( "Yes") );
		PORT_DIPNAME(0x08, 0x00, "Trackball Size" );
		PORT_DIPSETTING(   0x00, "Large" );
		PORT_DIPSETTING(   0x08, "Mini" );
		PORT_DIPNAME(0x70, 0x70, "Bonus City" );
		PORT_DIPSETTING(   0x10, "8000" );
		PORT_DIPSETTING(   0x70, "10000" );
		PORT_DIPSETTING(   0x60, "12000" );
		PORT_DIPSETTING(   0x50, "14000" );
		PORT_DIPSETTING(   0x40, "15000" );
		PORT_DIPSETTING(   0x30, "18000" );
		PORT_DIPSETTING(   0x20, "20000" );
		PORT_DIPSETTING(   0x00, "None" );
		PORT_DIPNAME(0x80, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(   0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(   0x80, DEF_STR( "Cocktail") );
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_X, 20, 10, 0, 0);
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_Y | IPF_REVERSE, 20, 10, 0, 0);
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_X | IPF_REVERSE | IPF_COCKTAIL, 20, 10, 0, 0);
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_Y | IPF_REVERSE | IPF_COCKTAIL, 20, 10, 0, 0);
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_suprmatk = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON3 | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN3 );
	
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON3 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x18, 0x00, IPT_UNUSED );/* trackball input, handled in machine/missile.c */
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BITX(0x40, IP_ACTIVE_LOW, IPT_SERVICE , DEF_STR( "Service_Mode"), KEYCODE_F2, IP_JOY_NONE );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_VBLANK );
	
		PORT_START(); 	/* IN2 */
		PORT_DIPNAME(0x03, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(   0x01, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(   0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(   0x03, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(   0x02, DEF_STR( "Free_Play") );
		PORT_DIPNAME(0x0c, 0x00, "Right Coin" );
		PORT_DIPSETTING(   0x00, "*1" );
		PORT_DIPSETTING(   0x04, "*4" );
		PORT_DIPSETTING(   0x08, "*5" );
		PORT_DIPSETTING(   0x0c, "*6" );
		PORT_DIPNAME(0x10, 0x00, "Center Coin" );
		PORT_DIPSETTING(   0x00, "*1" );
		PORT_DIPSETTING(   0x10, "*2" );
		PORT_DIPNAME(0x20, 0x20, DEF_STR( "Unknown") );
		PORT_DIPSETTING(   0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(   0x00, DEF_STR( "On") );
		PORT_DIPNAME(0xc0, 0x40, "Game" );
		PORT_DIPSETTING(   0x00, "Missile Command" );
		PORT_DIPSETTING(   0x40, "Easy Super Missile Attack" );
		PORT_DIPSETTING(   0x80, "Reg. Super Missile Attack" );
		PORT_DIPSETTING(   0xc0, "Hard Super Missile Attack" );
	
		PORT_START(); 	/* IN3 */
		PORT_DIPNAME(0x03, 0x00, "Cities" );
		PORT_DIPSETTING(   0x02, "4" );
		PORT_DIPSETTING(   0x01, "5" );
		PORT_DIPSETTING(   0x03, "6" );
		PORT_DIPSETTING(   0x00, "7" );
		PORT_DIPNAME(0x04, 0x04, "Bonus Credit for 4 Coins" );
		PORT_DIPSETTING(   0x04, DEF_STR( "No") );
		PORT_DIPSETTING(   0x00, DEF_STR( "Yes") );
		PORT_DIPNAME(0x08, 0x00, "Trackball Size" );
		PORT_DIPSETTING(   0x00, "Large" );
		PORT_DIPSETTING(   0x08, "Mini" );
		PORT_DIPNAME(0x70, 0x70, "Bonus City" );
		PORT_DIPSETTING(   0x10, "8000" );
		PORT_DIPSETTING(   0x70, "10000" );
		PORT_DIPSETTING(   0x60, "12000" );
		PORT_DIPSETTING(   0x50, "14000" );
		PORT_DIPSETTING(   0x40, "15000" );
		PORT_DIPSETTING(   0x30, "18000" );
		PORT_DIPSETTING(   0x20, "20000" );
		PORT_DIPSETTING(   0x00, "None" );
		PORT_DIPNAME(0x80, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(   0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(   0x80, DEF_STR( "Cocktail") );
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_X, 20, 10, 0, 0);
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_Y | IPF_REVERSE, 20, 10, 0, 0);
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_X | IPF_REVERSE | IPF_COCKTAIL, 20, 10, 0, 0);
	
		PORT_START(); 	/* FAKE */
		PORT_ANALOG( 0x0f, 0x00, IPT_TRACKBALL_Y | IPF_REVERSE | IPF_COCKTAIL, 20, 10, 0, 0);
	INPUT_PORTS_END(); }}; 
	
	
	
	static POKEYinterface pokey_interface = new POKEYinterface
	(
		1,	/* 1 chip */
		1250000,	/* 1.25 MHz??? */
		new int[] { 100 },
		/* The 8 pot handlers */
		new ReadHandlerPtr[] { null },
		new ReadHandlerPtr[] { null },
		new ReadHandlerPtr[] { null },
		new ReadHandlerPtr[] { null },
		new ReadHandlerPtr[] { null },
		new ReadHandlerPtr[] { null },
		new ReadHandlerPtr[] { null },
		new ReadHandlerPtr[] { null },
		/* The allpot handler */
		new ReadHandlerPtr[] { input_port_3_r }
	);
	
	
	
	static MachineDriver machine_driver_missile = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6502,
				1000000,	/* 1 MHz ???? */
				readmem,writemem,null,null,
				interrupt, 4  /* EEA was 1 */
			)
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		10,
		missile_init_machine,
	
		/* video hardware */
		256, 231, new rectangle( 0, 255, 0, 230 ),
		null,
		8, 0,
		null,
	
		VIDEO_TYPE_RASTER | VIDEO_SUPPORTS_DIRTY,
		null,
		missile_vh_start,
		missile_vh_stop,
		missile_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_POKEY,
				pokey_interface
			)
		}
	);
	
	
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_missile = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "035820.02",    0x5000, 0x0800, 0x7a62ce6a );
		ROM_LOAD( "035821.02",    0x5800, 0x0800, 0xdf3bd57f );
		ROM_LOAD( "035822.02",    0x6000, 0x0800, 0xa1cd384a );
		ROM_LOAD( "035823.02",    0x6800, 0x0800, 0x82e552bb );
		ROM_LOAD( "035824.02",    0x7000, 0x0800, 0x606e42e0 );
		ROM_LOAD( "035825.02",    0x7800, 0x0800, 0xf752eaeb );
		ROM_RELOAD( 		   0xF800, 0x0800 );	/* for interrupt vectors  */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_missile2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "35820-01.h1",  0x5000, 0x0800, 0x41cbb8f2 );
		ROM_LOAD( "35821-01.jk1", 0x5800, 0x0800, 0x728702c8 );
		ROM_LOAD( "35822-01.kl1", 0x6000, 0x0800, 0x28f0999f );
		ROM_LOAD( "35823-01.mn1", 0x6800, 0x0800, 0xbcc93c94 );
		ROM_LOAD( "35824-01.np1", 0x7000, 0x0800, 0x0ca089c8 );
		ROM_LOAD( "35825-01.r1",  0x7800, 0x0800, 0x428cf0d5 );
		ROM_RELOAD( 		      0xF800, 0x0800 );	/* for interrupt vectors  */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_suprmatk = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "035820.sma",   0x5000, 0x0800, 0x75f01b87 );
		ROM_LOAD( "035821.sma",   0x5800, 0x0800, 0x3320d67e );
		ROM_LOAD( "035822.sma",   0x6000, 0x0800, 0xe6be5055 );
		ROM_LOAD( "035823.sma",   0x6800, 0x0800, 0xa6069185 );
		ROM_LOAD( "035824.sma",   0x7000, 0x0800, 0x90a06be8 );
		ROM_LOAD( "035825.sma",   0x7800, 0x0800, 0x1298213d );
		ROM_RELOAD( 		   0xF800, 0x0800 );	/* for interrupt vectors  */
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_missile	   = new GameDriver("1980"	,"missile"	,"missile.java"	,rom_missile,null	,machine_driver_missile	,input_ports_missile	,null	,ROT0	,	"Atari", "Missile Command (set 1)" );
	public static GameDriver driver_missile2	   = new GameDriver("1980"	,"missile2"	,"missile.java"	,rom_missile2,driver_missile	,machine_driver_missile	,input_ports_missile	,null	,ROT0	,	"Atari", "Missile Command (set 2)" );
	public static GameDriver driver_suprmatk	   = new GameDriver("1981"	,"suprmatk"	,"missile.java"	,rom_suprmatk,driver_missile	,machine_driver_missile	,input_ports_suprmatk	,null	,ROT0	,	"Atari + Gencomp", "Super Missile Attack" );
}
