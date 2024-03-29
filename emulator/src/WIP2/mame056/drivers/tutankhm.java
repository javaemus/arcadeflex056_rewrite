/***************************************************************************

Tutankham :  memory map (preliminary)

driver by Mirko Buffoni

I include here the document based on Rob Jarrett's research because it's
really exaustive.



Tutankham Emu Info
------------------

By Rob Jarrett
robj@astound.com (until June 20, 1997)
or robncait@inforamp.net

Special thanks go to Pete Custerson for the schematics!!


I've recently been working on an emulator for Tutankham. Unfortunately,
time and resources are not on my side so I'd like to provide anyone with
the technical information I've gathered so far, that way someone can
finish the project.

First of all, I'd like to say that I've had no prior experience in
writing an emulator, and my hardware knowledge is weak. I've managed to
find out a fair amount by looking at the schematics of the game and the
disassembled ROMs. Using the USim C++ 6809 core I have the game sort of
up and running, albeit in a pathetic state. It's not playable, and
crashes after a short amount of time. I don't feel the source code is
worth releasing because of the bad design; I was using it as a testing
bed and anticipated rewriting everything in the future.

Here's all the info I know about Tutankham:

Processor: 6809
Sound: Z80 slave w/2 AY3910 sound chips
Graphics: Bitmapped display, no sprites (!)
Memory Map:

Address		R/W	Bits		Function
------------------------------------------------------------------------------------------------------
$0000-$7fff				Video RAM
					- Screen is stored sideways, 256x256 pixels
					- 1 byte=2 pixels
		R/W	aaaaxxxx	- leftmost pixel palette index
		R/W	xxxxbbbb	- rightmost pixel palette index
					- **** not correct **** Looks like some of this memory is for I/O state, (I think < $0100)
					  so you might want to blit from $0100-$7fff

$8000-$800f	R/W     aaaaaaaa	Palette colors
					- Don't know how to decode them into RGB values

$8100		W			Not sure
					- Video chip function of some sort
					( split screen y pan position -- TT )

$8120		R			Not sure
					- Read from quite frequently
					- Some sort of video or interrupt thing?
					- Or a random number seed?
					( watchdog reset -- NS )

$8160					Dip Switch 2
					- Inverted bits (ie. 1=off)
		R	xxxxxxxa	DSWI1
		R
		R			.
		R			.
		R			.
		R
		R
		R	axxxxxxx	DSWI8

$8180					I/O: Coin slots, service, 1P/2P buttons
		R

$81a0					Player 1 I/O
		R

$81c0					Player 2 I/O
		R

$81e0					Dip Switch 1
					- Inverted bits
		R	xxxxxxxa	DSWI1
		R
		R			.
		R			.
		R			.
		R
		R
		R	axxxxxxx	DSWI8

$8200					IST on schematics
					- Enable/disable IRQ
		R/W	xxxxxxxa	- a=1 IRQ can be fired, a=0 IRQ can't be fired

$8202					OUT2 (Coin counter)
		W	xxxxxxxa	- Increment coin counter

$8203					OUT1 (Coin counter)
		W	xxxxxxxa	- Increment coin counter

$8204					Not sure - 401 on schematics
		W

$8205					MUT on schematics
		R/W	xxxxxxxa	- Sound amplification on/off?

$8206					HFF on schematics
		W			- Don't know what it does
					( horizontal screen flip -- NS )

$8207					Not sure - can't resolve on schematics
		W
					( vertical screen flip -- NS )

$8300					Graphics bank select
		W	xxxxxaaa	- Selects graphics ROM 0-11 that appears at $9000-9fff
					- But wait! There's only 9 ROMs not 12! I think the PCB allows 12
					  ROMs for patches/mods to the game. Just make 9-11 return 0's

$8600		W			SON on schematics
					( trigger interrupt on audio CPU -- NS )
$8608		R/W			SON on schematics
					- Sound on/off? i.e. Run/halt Z80 sound CPU?

$8700		W	aaaaaaaa	SDA on schematics
					- Sound data? Maybe Z80 polls here and plays the appropriate sound?
					- If so, easy to trigger samples here

$8800-$8fff				RAM
		R/W			- Memory for the program ROMs

$9000-$9fff				Graphics ROMs ra1_1i.cpu - ra1_9i.cpu
		R	aaaaaaaa	- See address $8300 for usage

$a000-$afff				ROM ra1_1h.cpu
		R	aaaaaaaa	- 6809 Code

$b000-$bfff				ROM ra1_2h.cpu
		R	aaaaaaaa	- 6809 Code

$c000-$cfff				ROM ra1_3h.cpu
		R	aaaaaaaa	- 6809 Code

$d000-$dfff				ROM ra1_4h.cpu
		R	aaaaaaaa	- 6809 Code

$e000-$efff				ROM ra1_5h.cpu
		R	aaaaaaaa	- 6809 Code

$f000-$ffff				ROM ra1_6h.cpu
		R	aaaaaaaa	- 6809 Code

Programming notes:

I found that generating an IRQ every 4096 instructions seemed to kinda work. Again, I know
little about emu writing and I think some fooling with this number might be needed.

Sorry I didn't supply the DSW and I/O bits, this info is available elsewhere on the net, I
think at tant or something. I just couldn't remember what they were at this writing!!

If there are any questions at all, please feel free to email me at robj@astound.com (until
June 20, 1997) or robncait@inforamp.net.


BTW, this information is completely free - do as you wish with it. I'm not even sure if it's
correct! (Most of it seems to be). Giving me some credit if credit is due would be nice,
and please let me know about your emulator if you release it.


Sound board: uses the same board as Pooyan.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memcpy;
import WIP2.common.ptr.UBytePtr;
import static WIP2.mame056.common.*;

import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sound.streams.*;
import static WIP2.mame056.timerH.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sndhrdw.timeplt.timeplt_ay8910_interface;
import static WIP2.mame056.sndhrdw.timeplt.timeplt_sh_irqtrigger_w;
import static WIP2.mame056.sndhrdw.timeplt.timeplt_sound_readmem;
import static WIP2.mame056.sndhrdw.timeplt.timeplt_sound_writemem;
import static WIP2.mame056.sndintrf.*;

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.vidhrdw.tutankhm.*;

public class tutankhm
{
	
	public static WriteHandlerPtr tutankhm_bankselect_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int bankaddress;
		UBytePtr RAM = memory_region(REGION_CPU1);
	
	
		bankaddress = 0x10000 + (data & 0x0f) * 0x1000;
		cpu_setbank(1,new UBytePtr(RAM, bankaddress));
	} };
	
	public static WriteHandlerPtr tutankhm_coin_counter_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_counter_w.handler(offset ^ 1, data);
	} };
	
	public static WriteHandlerPtr flip_screen_x_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		flip_screen_x_set(data);
	} };
	
	public static WriteHandlerPtr flip_screen_y_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		flip_screen_y_set(data);
	} };
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_RAM ),
		new Memory_ReadAddress( 0x8120, 0x8120, watchdog_reset_r ),
		new Memory_ReadAddress( 0x8160, 0x8160, input_port_0_r ),	/* DSW2 (inverted bits) */
		new Memory_ReadAddress( 0x8180, 0x8180, input_port_1_r ),	/* IN0 I/O: Coin slots, service, 1P/2P buttons */
		new Memory_ReadAddress( 0x81a0, 0x81a0, input_port_2_r ),	/* IN1: Player 1 I/O */
		new Memory_ReadAddress( 0x81c0, 0x81c0, input_port_3_r ),	/* IN2: Player 2 I/O */
		new Memory_ReadAddress( 0x81e0, 0x81e0, input_port_4_r ),	/* DSW1 (inverted bits) */
		new Memory_ReadAddress( 0x8800, 0x8fff, MRA_RAM ),
		new Memory_ReadAddress( 0x9000, 0x9fff, MRA_BANK1 ),
		new Memory_ReadAddress( 0xa000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x7fff, tutankhm_videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x8000, 0x800f, paletteram_BBGGGRRR_w, paletteram ),
		new Memory_WriteAddress( 0x8100, 0x8100, MWA_RAM, tutankhm_scrollx ),
		new Memory_WriteAddress( 0x8200, 0x8200, interrupt_enable_w ),
		new Memory_WriteAddress( 0x8202, 0x8203, tutankhm_coin_counter_w ),
		new Memory_WriteAddress( 0x8205, 0x8205, MWA_NOP ),	/* ??? */
		new Memory_WriteAddress( 0x8206, 0x8206, flip_screen_x_w ),
		new Memory_WriteAddress( 0x8207, 0x8207, flip_screen_y_w ),
		new Memory_WriteAddress( 0x8300, 0x8300, tutankhm_bankselect_w ),
		new Memory_WriteAddress( 0x8600, 0x8600, timeplt_sh_irqtrigger_w ),
		new Memory_WriteAddress( 0x8700, 0x8700, soundlatch_w ),
		new Memory_WriteAddress( 0x8800, 0x8fff, MWA_RAM ),
		new Memory_WriteAddress( 0xa000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	static InputPortPtr input_ports_tutankhm = new InputPortPtr(){ public void handler() { 
		PORT_START();       /* DSW2 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );
		PORT_DIPSETTING(    0x01, "4" );
		PORT_DIPSETTING(    0x02, "5" );
		PORT_BITX( 0,       0x00, IPT_DIPSWITCH_SETTING | IPF_CHEAT, "256", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "30000" );
		PORT_DIPSETTING(    0x00, "40000" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x30, "Easy" );
		PORT_DIPSETTING(    0x10, "Normal" );
		PORT_DIPSETTING(    0x20, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x40, 0x40, "Flash Bomb" );
		PORT_DIPSETTING(    0x40, "1 per Life" );
		PORT_DIPSETTING(    0x00, "1 per Game" );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON3 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();       /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON3 | IPF_COCKTAIL );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x0f, 0x0f, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x02, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0x0f, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x0e, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_5C") );
		PORT_DIPSETTING(    0x0d, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x0b, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0x0a, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x09, DEF_STR( "1C_7C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Free_Play") );
		PORT_DIPNAME( 0xf0, 0xf0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x20, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x50, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0xf0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x70, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0xe0, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x60, DEF_STR( "2C_5C") );
		PORT_DIPSETTING(    0xd0, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0xb0, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0xa0, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x90, DEF_STR( "1C_7C") );
		PORT_DIPSETTING(    0x00, "Disabled" );
	/* 0x00 not commented out since the game makes the usual sound if you insert the coin */
	INPUT_PORTS_END(); }}; 
	
	
	
	static MachineDriver machine_driver_tutankhm = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6809,
				1500000,			/* 1.5 MHz ??? */
				readmem,writemem,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80 | CPU_AUDIO_CPU,
				14318180/8,	/* 1.789772727 MHz */
				timeplt_sound_readmem,timeplt_sound_writemem,null,null,
				ignore_interrupt,1	/* interrupts are triggered by the main CPU */
			)
		},
		30, DEFAULT_30HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* 1 CPU slice per frame - interleaving is forced when a sound command is written */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),	/* not sure about the visible area */
		null,					/* GfxDecodeInfo * */
		16, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,						/* vh_init routine */
		generic_vh_start,					/* vh_start routine */
		generic_vh_stop,					/* vh_stop routine */
		tutankhm_vh_screenrefresh,				/* vh_update routine */
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_AY8910,
				timeplt_ay8910_interface
			)
		}
	);
	
	
	static RomLoadPtr rom_tutankhm = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x20000, REGION_CPU1, 0 );     /* 64k for M6809 CPU code + 64k for ROM banks */
		ROM_LOAD( "h1.bin",       0x0a000, 0x1000, 0xda18679f );/* program ROMs */
		ROM_LOAD( "h2.bin",       0x0b000, 0x1000, 0xa0f02c85 );
		ROM_LOAD( "h3.bin",       0x0c000, 0x1000, 0xea03a1ab );
		ROM_LOAD( "h4.bin",       0x0d000, 0x1000, 0xbd06fad0 );
		ROM_LOAD( "h5.bin",       0x0e000, 0x1000, 0xbf9fd9b0 );
		ROM_LOAD( "h6.bin",       0x0f000, 0x1000, 0xfe079c5b );
		ROM_LOAD( "j1.bin",       0x10000, 0x1000, 0x7eb59b21 );/* graphic ROMs (banked) -- only 9 of 12 are filled */
		ROM_LOAD( "j2.bin",       0x11000, 0x1000, 0x6615eff3 );
		ROM_LOAD( "j3.bin",       0x12000, 0x1000, 0xa10d4444 );
		ROM_LOAD( "j4.bin",       0x13000, 0x1000, 0x58cd143c );
		ROM_LOAD( "j5.bin",       0x14000, 0x1000, 0xd7e7ae95 );
		ROM_LOAD( "j6.bin",       0x15000, 0x1000, 0x91f62b82 );
		ROM_LOAD( "j7.bin",       0x16000, 0x1000, 0xafd0a81f );
		ROM_LOAD( "j8.bin",       0x17000, 0x1000, 0xdabb609b );
		ROM_LOAD( "j9.bin",       0x18000, 0x1000, 0x8ea9c6a6 );
		/* the other banks (1900-1fff) are empty */
	
		ROM_REGION(  0x10000 , REGION_CPU2, 0 );/* 64k for Z80 sound CPU code */
		ROM_LOAD( "11-7a.bin",    0x0000, 0x1000, 0xb52d01fa );
		ROM_LOAD( "10-8a.bin",    0x1000, 0x1000, 0x9db5c0ce );
	ROM_END(); }}; 
	
	
	static RomLoadPtr rom_tutankst = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x20000, REGION_CPU1, 0 );     /* 64k for M6809 CPU code + 64k for ROM banks */
		ROM_LOAD( "h1.bin",       0x0a000, 0x1000, 0xda18679f );/* program ROMs */
		ROM_LOAD( "h2.bin",       0x0b000, 0x1000, 0xa0f02c85 );
		ROM_LOAD( "ra1_3h.cpu",   0x0c000, 0x1000, 0x2d62d7b1 );
		ROM_LOAD( "h4.bin",       0x0d000, 0x1000, 0xbd06fad0 );
		ROM_LOAD( "h5.bin",       0x0e000, 0x1000, 0xbf9fd9b0 );
		ROM_LOAD( "ra1_6h.cpu",   0x0f000, 0x1000, 0xc43b3865 );
		ROM_LOAD( "j1.bin",       0x10000, 0x1000, 0x7eb59b21 );/* graphic ROMs (banked) -- only 9 of 12 are filled */
		ROM_LOAD( "j2.bin",       0x11000, 0x1000, 0x6615eff3 );
		ROM_LOAD( "j3.bin",       0x12000, 0x1000, 0xa10d4444 );
		ROM_LOAD( "j4.bin",       0x13000, 0x1000, 0x58cd143c );
		ROM_LOAD( "j5.bin",       0x14000, 0x1000, 0xd7e7ae95 );
		ROM_LOAD( "j6.bin",       0x15000, 0x1000, 0x91f62b82 );
		ROM_LOAD( "j7.bin",       0x16000, 0x1000, 0xafd0a81f );
		ROM_LOAD( "j8.bin",       0x17000, 0x1000, 0xdabb609b );
		ROM_LOAD( "j9.bin",       0x18000, 0x1000, 0x8ea9c6a6 );
		/* the other banks (1900-1fff) are empty */
	
		ROM_REGION(  0x10000 , REGION_CPU2, 0 );/* 64k for Z80 sound CPU code */
		ROM_LOAD( "11-7a.bin",    0x0000, 0x1000, 0xb52d01fa );
		ROM_LOAD( "10-8a.bin",    0x1000, 0x1000, 0x9db5c0ce );
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_tutankhm	   = new GameDriver("1982"	,"tutankhm"	,"tutankhm.java"	,rom_tutankhm,null	,machine_driver_tutankhm	,input_ports_tutankhm	,null	,ROT90	,	"Konami", "Tutankham" );
	public static GameDriver driver_tutankst	   = new GameDriver("1982"	,"tutankst"	,"tutankhm.java"	,rom_tutankst,driver_tutankhm	,machine_driver_tutankhm	,input_ports_tutankhm	,null	,ROT90	,	"[Konami] (Stern license)", "Tutankham (Stern)" );
}
