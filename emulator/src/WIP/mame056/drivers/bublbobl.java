/***************************************************************************
Bubble Bobble / Tokio

Main clock: XTAL = 24 MHz
Horizontal video frequency: HSYNC = XTAL/4/384 = 15.625 kHz
Video frequency: VSYNC = HSYNC/264 = 59.185606 Hz
VBlank duration: 1/VSYNC * (40/264) = 2560 us

***************************************************************************

Bubble Bobble ROM info

CPU Board
---------
           | Taito  |Romstar | ?????  |Romstar |
           |        |        |missing |mode sel|
17  CU1    | A78-01 |   ->   |   ->   |   ->   |   protection mcu
49  PAL1   | A78-02 |   ->   |   ->   |   ->   |   address decoder
43  PAL2   | A78-03 |   ->   |   ->   |   ->   |   address decoder
12  PAL3   | A78-04 |   ->   |   ->   |   ->   |   address decoder
53  empty  |        |        |        |        |   main prg
52  ROM1   | A78-05 | A78-21 | A78-22 | A78-24 |   main prg
51  ROM2   | A78-06 |   ->   | A78-23 | A78-25 |   main prg
46  ROM4   | A78-07 |   ->   |   ->   |   ->   |   sound prg
37  ROM3   | A78-08 |   ->   |   ->   |   ->   |   sub prg

Video Board
-----------
12  ROM1   | A78-09 |   ->   |   ->   |   ->   |   gfx
13  ROM2   | A78-10 |   ->   |   ->   |   ->   |   gfx
14  ROM3   | A78-11 |   ->   |   ->   |   ->   |   gfx
15  ROM4   | A78-12 |   ->   |   ->   |   ->   |   gfx
16  ROM5   | A78-13 |   ->   |   ->   |   ->   |   gfx
17  ROM6   | A78-14 |   ->   |   ->   |   ->   |   gfx
18  empty  |        |        |        |        |   gfx
19  empty  |        |        |        |        |   gfx
30  ROM7   | A78-15 |   ->   |   ->   |   ->   |   gfx
31  ROM8   | A78-16 |   ->   |   ->   |   ->   |   gfx
32  ROM9   | A78-17 |   ->   |   ->   |   ->   |   gfx
33  ROM10  | A78-18 |   ->   |   ->   |   ->   |   gfx
34  ROM11  | A78-19 |   ->   |   ->   |   ->   |   gfx
35  ROM12  | A78-20 |   ->   |   ->   |   ->   |   gfx
36  empty  |        |        |        |        |   gfx
37  empty  |        |        |        |        |   gfx
41  ROM13  | A71-25 |   ->   |   ->   |   ->   |   video timing


Bobble Bobble memory map

driver by Chris Moore

CPU #1
0000-bfff ROM (8000-bfff is banked)
c000-dcff Graphic RAM. This contains pointers to the video RAM columns and
          to the sprites are contained in Object RAM.
dd00-dfff Object RAM (groups of four bytes: X position, code [offset in the
          Graphic RAM], Y position, gfx bank)
CPU #2
0000-7fff ROM

CPU #1 AND #2
e000-f7fe RAM
f800-f9ff Palette RAM
fc01-fdff RAM

read:
ff00      DSWA
ff01      DSWB
ff02      IN0
ff03      IN1


Service mode works only if the language switch is set to Japanese.

- The protection feature which randomizes the EXTEND letters in the original
  version is not emulated properly.

***************************************************************************

Tokio memory map

CPU 1
0000-bfff ROM (8000-bfff is banked)
c000-dcff Graphic RAM. This contains pointers to the video RAM columns and
          to the sprites contained in Object RAM.
dd00-dfff Object RAM (groups of four bytes: X position, code [offset in the
          Graphic RAM], Y position, gfx bank)
e000-f7ff RAM (Shared)
f800-f9ff Palette RAM

fa03 - DSW0
fa04 - DSW1
fa05 - Coins
fa06 - Controls Player 1
fa07 - Controls Player 1

CPU 2
0000-7fff ROM
8000-97ff RAM (Shared)

CPU 3
0000-7fff ROM
8000-8fff RAM


  Here goes a list of known deficiencies of our drivers:

  - The bootleg romset is functional. The original one hangs at
    the title screen (protection).

  - Sound support is probably incomplete. There are a couple of unknown
    accesses done by the CPU, including to the YM2203 I/O ports. At the
	very least, there should be some filters.

  - "fake-r" routine make the "original" roms to restart the game after
    some seconds.

    Well, we know very little about the 0xFE00 address. It could be
    some watchdog or a synchronization timer.

    I remember scanning the main CPU code to find how it was
    used on the bootleg set. Then I just figured out a constant value
    that made the game run (it hang if just set unhandled, that is,
    returning zero).

    Maybe the solution is to patch the bootleg ROMs to skip some tests
    at this location (I remember some of them being in the
    initialization routine of the main CPU).

                       Marcelo de G. Malheiros <malheiro@dca.fee.unicamp.br>
                                                                   1998.9.25

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.machine.bublbobl.*;
import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;

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
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;

import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static mame056.palette.*;
import static WIP2.mame056.inptport.*;

import static WIP.mame056.vidhrdw.bublbobl.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sound._2203intf.*;
import static WIP2.mame056.sound._2203intfH.*;
import static WIP2.mame056.sound._3526intf.*;
import static WIP2.mame056.sound._3812intfH.*;
import static WIP2.mame056.sound._3812intf.*;
import static WIP2.common.libc.cstring.*;

public class bublbobl
{
	
	public static Memory_ReadAddress bublbobl_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_BANK1 ),
		new Memory_ReadAddress( 0xc000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_r ),
		new Memory_ReadAddress( 0xf800, 0xf9ff, paletteram_r ),
		new Memory_ReadAddress( 0xfc00, 0xffff, bublbobl_sharedram2_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress bublbobl_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0xbfff, MWA_ROM ),
		new Memory_WriteAddress( 0xc000, 0xdcff, MWA_RAM, videoram, videoram_size ),
		new Memory_WriteAddress( 0xdd00, 0xdfff, MWA_RAM, bublbobl_objectram, bublbobl_objectram_size ),
		new Memory_WriteAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_w, bublbobl_sharedram1 ),
		new Memory_WriteAddress( 0xf800, 0xf9ff, paletteram_RRRRGGGGBBBBxxxx_swap_w, paletteram ),
		new Memory_WriteAddress( 0xfa00, 0xfa00, bublbobl_sound_command_w ),
	//	new Memory_WriteAddress( 0xfa03, 0xfa03,  ), clocks reset to sound cpu
		new Memory_WriteAddress( 0xfa80, 0xfa80, watchdog_reset_w ),
		new Memory_WriteAddress( 0xfb00, 0xfb00, bublbobl_nmitrigger_w ),	/* not used by Bubble Bobble, only by Tokio */
		new Memory_WriteAddress( 0xfb40, 0xfb40, bublbobl_bankswitch_w ),
		new Memory_WriteAddress( 0xfc00, 0xffff, bublbobl_sharedram2_w, bublbobl_sharedram2 ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress m68705_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x0000, bublbobl_68705_portA_r ),
		new Memory_ReadAddress( 0x0001, 0x0001, bublbobl_68705_portB_r ),
		new Memory_ReadAddress( 0x0002, 0x0002, input_port_0_r ),	/* COIN */
		new Memory_ReadAddress( 0x0010, 0x007f, MRA_RAM ),
		new Memory_ReadAddress( 0x0080, 0x07ff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress m68705_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x0000, bublbobl_68705_portA_w ),
		new Memory_WriteAddress( 0x0001, 0x0001, bublbobl_68705_portB_w ),
		new Memory_WriteAddress( 0x0004, 0x0004, bublbobl_68705_ddrA_w ),
		new Memory_WriteAddress( 0x0005, 0x0005, bublbobl_68705_ddrB_w ),
		new Memory_WriteAddress( 0x0010, 0x007f, MWA_RAM ),
		new Memory_WriteAddress( 0x0080, 0x07ff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress boblbobl_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_BANK1 ),
		new Memory_ReadAddress( 0xc000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_r ),
		new Memory_ReadAddress( 0xf800, 0xf9ff, paletteram_r ),
		new Memory_ReadAddress( 0xfc00, 0xfcff, bublbobl_sharedram2_r ),
		new Memory_ReadAddress( 0xff00, 0xff00, input_port_0_r ),
		new Memory_ReadAddress( 0xff01, 0xff01, input_port_1_r ),
		new Memory_ReadAddress( 0xff02, 0xff02, input_port_2_r ),
		new Memory_ReadAddress( 0xff03, 0xff03, input_port_3_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress boblbobl_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0xbfff, MWA_ROM ),
		new Memory_WriteAddress( 0xc000, 0xdcff, MWA_RAM, videoram, videoram_size ),
		new Memory_WriteAddress( 0xdd00, 0xdfff, MWA_RAM, bublbobl_objectram, bublbobl_objectram_size ),
		new Memory_WriteAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_w, bublbobl_sharedram1 ),
		new Memory_WriteAddress( 0xf800, 0xf9ff, paletteram_RRRRGGGGBBBBxxxx_swap_w, paletteram ),
		new Memory_WriteAddress( 0xfa00, 0xfa00, bublbobl_sound_command_w ),
		new Memory_WriteAddress( 0xfa80, 0xfa80, MWA_NOP ),
		new Memory_WriteAddress( 0xfb00, 0xfb00, bublbobl_nmitrigger_w ),	/* not used by Bubble Bobble, only by Tokio */
		new Memory_WriteAddress( 0xfb40, 0xfb40, bublbobl_bankswitch_w ),
		new Memory_WriteAddress( 0xfc00, 0xfcff, bublbobl_sharedram2_w, bublbobl_sharedram2 ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress bublbobl_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress bublbobl_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x8fff, MRA_RAM ),
		new Memory_ReadAddress( 0x9000, 0x9000, YM2203_status_port_0_r ),
		new Memory_ReadAddress( 0x9001, 0x9001, YM2203_read_port_0_r ),
		new Memory_ReadAddress( 0xa000, 0xa000, YM3526_status_port_0_r ),
		new Memory_ReadAddress( 0xb000, 0xb000, soundlatch_r ),
		new Memory_ReadAddress( 0xb001, 0xb001, MRA_NOP ),	/* bit 0: message pending for main cpu */
										/* bit 1: message pending for sound cpu */
		new Memory_ReadAddress( 0xe000, 0xefff, MRA_ROM ),	/* space for diagnostic ROM? */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0x8000, 0x8fff, MWA_RAM ),
		new Memory_WriteAddress( 0x9000, 0x9000, YM2203_control_port_0_w ),
		new Memory_WriteAddress( 0x9001, 0x9001, YM2203_write_port_0_w ),
		new Memory_WriteAddress( 0xa000, 0xa000, YM3526_control_port_0_w ),
		new Memory_WriteAddress( 0xa001, 0xa001, YM3526_write_port_0_w ),
		new Memory_WriteAddress( 0xb000, 0xb000, MWA_NOP ),	/* message for main cpu */
		new Memory_WriteAddress( 0xb001, 0xb001, bublbobl_sh_nmi_enable_w ),
		new Memory_WriteAddress( 0xb002, 0xb002, bublbobl_sh_nmi_disable_w ),
		new Memory_WriteAddress( 0xe000, 0xefff, MWA_ROM ),	/* space for diagnostic ROM? */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress tokio_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_BANK1 ),
		new Memory_ReadAddress( 0xc000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_r ),
		new Memory_ReadAddress( 0xf800, 0xf9ff, paletteram_r ),
		new Memory_ReadAddress( 0xfa03, 0xfa03, input_port_0_r ),
		new Memory_ReadAddress( 0xfa04, 0xfa04, input_port_1_r ),
		new Memory_ReadAddress( 0xfa05, 0xfa05, input_port_2_r ),
		new Memory_ReadAddress( 0xfa06, 0xfa06, input_port_3_r ),
		new Memory_ReadAddress( 0xfa07, 0xfa07, input_port_4_r ),
		new Memory_ReadAddress( 0xfe00, 0xfe00, tokio_fake_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress tokio_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0xbfff, MWA_ROM ),
		new Memory_WriteAddress( 0xc000, 0xdcff, MWA_RAM, videoram, videoram_size ),
		new Memory_WriteAddress( 0xdd00, 0xdfff, MWA_RAM, bublbobl_objectram, bublbobl_objectram_size ),
		new Memory_WriteAddress( 0xe000, 0xf7ff, bublbobl_sharedram1_w, bublbobl_sharedram1 ),
		new Memory_WriteAddress( 0xf800, 0xf9ff, paletteram_RRRRGGGGBBBBxxxx_swap_w, paletteram ),
		new Memory_WriteAddress( 0xfa00, 0xfa00, MWA_NOP ),
		new Memory_WriteAddress( 0xfa80, 0xfa80, tokio_bankswitch_w ),
		new Memory_WriteAddress( 0xfb00, 0xfb00, tokio_videoctrl_w ),
		new Memory_WriteAddress( 0xfb80, 0xfb80, bublbobl_nmitrigger_w ),
		new Memory_WriteAddress( 0xfc00, 0xfc00, bublbobl_sound_command_w ),
		new Memory_WriteAddress( 0xfe00, 0xfe00, MWA_NOP ), /* ??? */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress tokio_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x97ff, bublbobl_sharedram1_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress tokio_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0x8000, 0x97ff, bublbobl_sharedram1_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress tokio_sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x8fff, MRA_RAM ),
		new Memory_ReadAddress( 0x9000, 0x9000, soundlatch_r ),
	//	new Memory_ReadAddress( 0x9800, 0x9800, MRA_NOP ),	/* ??? */
		new Memory_ReadAddress( 0xb000, 0xb000, YM2203_status_port_0_r ),
		new Memory_ReadAddress( 0xb001, 0xb001, YM2203_read_port_0_r ),
		new Memory_ReadAddress( 0xe000, 0xefff, MRA_ROM ),	/* space for diagnostic ROM? */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress tokio_sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0x8000, 0x8fff, MWA_RAM ),
	//	new Memory_WriteAddress( 0x9000, 0x9000, MWA_NOP ),	/* ??? */
		new Memory_WriteAddress( 0xa000, 0xa000, bublbobl_sh_nmi_disable_w ),
		new Memory_WriteAddress( 0xa800, 0xa800, bublbobl_sh_nmi_enable_w ),
		new Memory_WriteAddress( 0xb000, 0xb000, YM2203_control_port_0_w ),
		new Memory_WriteAddress( 0xb001, 0xb001, YM2203_write_port_0_w ),
		new Memory_WriteAddress( 0xe000, 0xefff, MWA_ROM ),	/* space for diagnostic ROM? */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	static InputPortPtr input_ports_bublbobl = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_TILT );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SERVICE1 );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_COIN1 );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_COIN2 );	PORT_BIT( 0xf0, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START();       /* DSW0 */
		PORT_DIPNAME( 0x01, 0x00, "Language" );	PORT_DIPSETTING(    0x01, "Japanese" );	PORT_DIPSETTING(    0x00, "English" );	PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );	PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x10, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
	
		PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );	PORT_DIPSETTING(    0x03, "Medium" );	PORT_DIPSETTING(    0x01, "Hard" );	PORT_DIPSETTING(    0x00, "Hardest" );	PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "20000 80000" );	PORT_DIPSETTING(    0x0c, "30000 100000" );	PORT_DIPSETTING(    0x04, "40000 200000" );	PORT_DIPSETTING(    0x00, "50000 250000" );	PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x10, "1" );	PORT_DIPSETTING(    0x00, "2" );	PORT_DIPSETTING(    0x30, "3" );	PORT_DIPSETTING(    0x20, "5" );	PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_START();       /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY | IPF_PLAYER2 );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_PLAYER2 );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_boblbobl = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW0 */
		PORT_DIPNAME( 0x01, 0x00, "Language" );	PORT_DIPSETTING(    0x00, "English" );	PORT_DIPSETTING(    0x01, "Japanese" );	PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );	PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x10, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
	
		PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );	PORT_DIPSETTING(    0x03, "Medium" );	PORT_DIPSETTING(    0x01, "Hard" );	PORT_DIPSETTING(    0x00, "Hardest" );	PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "20000 80000" );	PORT_DIPSETTING(    0x0c, "30000 100000" );	PORT_DIPSETTING(    0x04, "40000 200000" );	PORT_DIPSETTING(    0x00, "50000 250000" );	PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x10, "1" );	PORT_DIPSETTING(    0x00, "2" );	PORT_DIPSETTING(    0x30, "3" );	PORT_DIPSETTING(    0x20, "5" );	PORT_DIPNAME( 0xc0, 0x00, "Monster Speed" );	PORT_DIPSETTING(    0x00, "Normal" );	PORT_DIPSETTING(    0x40, "Medium" );	PORT_DIPSETTING(    0x80, "High" );	PORT_DIPSETTING(    0xc0, "Very High" );
		PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_COIN2 );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_COIN1 );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY | IPF_PLAYER2 );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_PLAYER2 );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_TILT );/* ?????*/
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_SERVICE1 );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_sboblbob = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW0 */
		PORT_DIPNAME( 0x01, 0x00, "Game" );	PORT_DIPSETTING(    0x01, "Bobble Bobble" );	PORT_DIPSETTING(    0x00, "Super Bobble Bobble" );	PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );	PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x10, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
	
		PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );	PORT_DIPSETTING(    0x03, "Medium" );	PORT_DIPSETTING(    0x01, "Hard" );	PORT_DIPSETTING(    0x00, "Hardest" );	PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "20000 80000" );	PORT_DIPSETTING(    0x0c, "30000 100000" );	PORT_DIPSETTING(    0x04, "40000 200000" );	PORT_DIPSETTING(    0x00, "50000 250000" );	PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x10, "1" );	PORT_DIPSETTING(    0x00, "2" );	PORT_DIPSETTING(    0x30, "3" );	PORT_BITX( 0,       0x20, IPT_DIPSWITCH_SETTING | IPF_CHEAT, "100", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPNAME( 0xc0, 0x00, "Monster Speed" );	PORT_DIPSETTING(    0x00, "Normal" );	PORT_DIPSETTING(    0x40, "Medium" );	PORT_DIPSETTING(    0x80, "High" );	PORT_DIPSETTING(    0xc0, "Very High" );
		PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_COIN2 );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_COIN1 );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY | IPF_PLAYER2 );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_PLAYER2 );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_TILT );/* ?????*/
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_SERVICE1 );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_tokio = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW0 */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );	PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x10, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
	
		PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x02, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x03, "Easy" );	PORT_DIPSETTING(    0x02, "Medium" );	PORT_DIPSETTING(    0x01, "Hard" );	PORT_DIPSETTING(    0x00, "Hardest" );	PORT_DIPNAME( 0x0c, 0x08, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x0C, "100000 400000" );	PORT_DIPSETTING(    0x08, "200000 400000" );	PORT_DIPSETTING(    0x04, "300000 400000" );	PORT_DIPSETTING(    0x00, "400000 400000" );	PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x30, "3" );	PORT_DIPSETTING(    0x20, "4" );	PORT_DIPSETTING(    0x10, "5" );	PORT_BITX( 0,       0x00, IPT_DIPSWITCH_SETTING | IPF_CHEAT, "99", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, "Language" );	PORT_DIPSETTING(    0x00, "English" );	PORT_DIPSETTING(    0x80, "Japanese" );
		PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_TILT );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SERVICE1 );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_COIN1 );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_COIN2 );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_2WAY );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_2WAY );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_START();       /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* the characters are 8x8 pixels */
		256*8*8,	/* 256 chars per bank * 8 banks per ROM pair * 8 ROM pairs */
		4,	/* 4 bits per pixel */
		new int[] { 0, 4, 8*0x8000*8, 8*0x8000*8+4 },
		new int[] { 3, 2, 1, 0, 8+3, 8+2, 8+1, 8+0 },
		new int[] { 0*16, 1*16, 2*16, 3*16, 4*16, 5*16, 6*16, 7*16 },
		16*8	/* every char takes 16 bytes in two ROMs */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		/* read all graphics into one big graphics region */
		new GfxDecodeInfo( REGION_GFX1, 0x00000, charlayout, 0, 16 ),
		new GfxDecodeInfo( -1 )	/* end of array */
	};
	
	
	
	public static int MAIN_XTAL = 24000000;
	
	/* handler called by the 2203 emulator when the internal timers cause an IRQ */
	static WriteYmHandlerPtr irqhandler = new WriteYmHandlerPtr() {
            public void handler(int irq) {
                cpu_set_irq_line(2,0,irq!=0 ? ASSERT_LINE : CLEAR_LINE);
            }
        };
	
	static YM2203interface ym2203_interface = new YM2203interface
	(
		1,			/* 1 chip */
		MAIN_XTAL/8,	/* 3 MHz */
		new int[]{ YM2203_VOL(25,25) },
		new ReadHandlerPtr[]{ null },
		new ReadHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteYmHandlerPtr[]{ irqhandler }
	);
	
	
	static YM3526interface ym3526_interface = new YM3526interface
	(
		1,			/* 1 chip (no more supported) */
		MAIN_XTAL/8,	/* 3 MHz */
		new int[]{ 25 }		/* volume */
	);
	
	
	static YM2203interface tokio_ym2203_interface = new YM2203interface
	(
		1,		/* 1 chip */
		MAIN_XTAL/8,	/* 3 MHz */
		new int[]{ YM2203_VOL(100,20) },
		new ReadHandlerPtr[]{ null },
		new ReadHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteYmHandlerPtr[]{ irqhandler }
	);
	
	
	
	static MachineDriver machine_driver_bublbobl = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				MAIN_XTAL/4,	/* 6 MHz */
				bublbobl_readmem,bublbobl_writemem,null,null,
				ignore_interrupt,0	/* IRQs are triggered by the 68705 */
			),
			new MachineCPU(
				CPU_Z80,
				MAIN_XTAL/4,	/* 6 MHz */
				bublbobl_readmem2,bublbobl_writemem2,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80 | CPU_AUDIO_CPU,
				MAIN_XTAL/8,	/* 3 MHz */
				sound_readmem,sound_writemem,null,null,
				ignore_interrupt,0	/* NMIs are triggered by the main CPU */
									/* IRQs are triggered by the YM2203 */
			),
			new MachineCPU(
				CPU_M68705,
				4000000/2,	/* xtal is 4MHz, I think it's divided by 2 internally */
				m68705_readmem,m68705_writemem,null,null,
				bublbobl_m68705_interrupt,2	/* ??? should come from the same */
						/* clock which latches the INT pin on the second Z80 */
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		100,	/* 100 CPU slices per frame - an high value to ensure proper */
				/* synchronization of the CPUs */
		null,		/* init_machine() */
	
		/* video hardware */
		32*8, 32*8,	new rectangle( 0, 32*8-1, 2*8, 30*8-1 ),
		gfxdecodeinfo,
		256, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		bublbobl_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			),
			new MachineSound(
				SOUND_YM3526,
				ym3526_interface
			)
		}
	);
	
	static MachineDriver machine_driver_boblbobl = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				MAIN_XTAL/4,	/* 6 MHz */
				boblbobl_readmem,boblbobl_writemem,null,null,
				interrupt,1	/* interrupt mode 1, unlike Bubble Bobble */
			),
			new MachineCPU(
				CPU_Z80,
				MAIN_XTAL/4,	/* 6 MHz */
				bublbobl_readmem2,bublbobl_writemem2,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80 | CPU_AUDIO_CPU,
				MAIN_XTAL/8,	/* 3 MHz */
				sound_readmem,sound_writemem,null,null,
				ignore_interrupt,0	/* NMIs are triggered by the main CPU */
									/* IRQs are triggered by the YM2203 */
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		100,	/* 100 CPU slices per frame - an high value to ensure proper */
				/* synchronization of the CPUs */
		null,		/* init_machine() */
	
		/* video hardware */
		32*8, 32*8,	new rectangle( 0, 32*8-1, 2*8, 30*8-1 ),
		gfxdecodeinfo,
		256, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		bublbobl_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			),
			new MachineSound(
				SOUND_YM3526,
				ym3526_interface
			)
		}
	);
	
	static MachineDriver machine_driver_tokio = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				MAIN_XTAL/4,	/* 6 MHz */
				tokio_readmem,tokio_writemem,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80,
				MAIN_XTAL/4,	/* 6 MHz */
				tokio_readmem2,tokio_writemem2,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80 | CPU_AUDIO_CPU,
				MAIN_XTAL/8,	/* 3 MHz */
				tokio_sound_readmem,tokio_sound_writemem,null,null,
				ignore_interrupt,0
							/* NMIs are triggered by the main CPU */
							/* IRQs are triggered by the YM2203 */
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION, /* frames/second, vblank duration */
		100,	/* 100 CPU slices per frame - an high value to ensure proper */
				/* synchronization of the CPUs */
		null,	/* init_machine() */
	
		/* video hardware */
		32*8, 32*8,	new rectangle( 0, 32*8-1, 2*8, 30*8-1 ),
		gfxdecodeinfo,
		256, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		bublbobl_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				tokio_ym2203_interface
			)
		}
	);
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_bublbobl = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );	ROM_LOAD( "a78-06.51",    0x00000, 0x08000, 0x32c8305b );    /* ROMs banked at 8000-bfff */
		ROM_LOAD( "a78-05.52",    0x10000, 0x10000, 0x53f4bc6e );	/* 20000-2ffff empty */
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "a78-08.37",    0x0000, 0x08000, 0xae11a07b );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU */
		ROM_LOAD( "a78-07.46",    0x0000, 0x08000, 0x4f9a26e8 );
		ROM_REGION( 0x0800, REGION_CPU4, 0 );/* 2k for the microcontroller */
		ROM_LOAD( "68705.bin",    0x0000, 0x0800, 0x78caa635 );/* from a pirate board */
	
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );	ROM_LOAD( "a78-09.12",    0x00000, 0x8000, 0x20358c22 );   /* 1st plane */
		ROM_LOAD( "a78-10.13",    0x08000, 0x8000, 0x930168a9 );	ROM_LOAD( "a78-11.14",    0x10000, 0x8000, 0x9773e512 );	ROM_LOAD( "a78-12.15",    0x18000, 0x8000, 0xd045549b );	ROM_LOAD( "a78-13.16",    0x20000, 0x8000, 0xd0af35c5 );	ROM_LOAD( "a78-14.17",    0x28000, 0x8000, 0x7b5369a8 );	/* 0x30000-0x3ffff empty */
		ROM_LOAD( "a78-15.30",    0x40000, 0x8000, 0x6b61a413 );   /* 2nd plane */
		ROM_LOAD( "a78-16.31",    0x48000, 0x8000, 0xb5492d97 );	ROM_LOAD( "a78-17.32",    0x50000, 0x8000, 0xd69762d5 );	ROM_LOAD( "a78-18.33",    0x58000, 0x8000, 0x9f243b68 );	ROM_LOAD( "a78-19.34",    0x60000, 0x8000, 0x66e9438c );	ROM_LOAD( "a78-20.35",    0x68000, 0x8000, 0x9ef863ad );	/* 0x70000-0x7ffff empty */
	
		ROM_REGION( 0x0100, REGION_PROMS, 0 );	ROM_LOAD( "a71-25.41",    0x0000, 0x0100, 0x2d0f8545 );/* video timing */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_bublbobr = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );	ROM_LOAD( "a78-25.51",    0x00000, 0x08000, 0x2d901c9d );    /* ROMs banked at 8000-bfff */
		ROM_LOAD( "a78-24.52",    0x10000, 0x10000, 0xb7afedc4 );	/* 20000-2ffff empty */
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "a78-08.37",    0x0000, 0x08000, 0xae11a07b );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU */
		ROM_LOAD( "a78-07.46",    0x0000, 0x08000, 0x4f9a26e8 );
		ROM_REGION( 0x0800, REGION_CPU4, 0 );/* 2k for the microcontroller */
		ROM_LOAD( "68705.bin",    0x0000, 0x0800, 0x78caa635 );/* from a pirate board */
	
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );	ROM_LOAD( "a78-09.12",    0x00000, 0x8000, 0x20358c22 );   /* 1st plane */
		ROM_LOAD( "a78-10.13",    0x08000, 0x8000, 0x930168a9 );	ROM_LOAD( "a78-11.14",    0x10000, 0x8000, 0x9773e512 );	ROM_LOAD( "a78-12.15",    0x18000, 0x8000, 0xd045549b );	ROM_LOAD( "a78-13.16",    0x20000, 0x8000, 0xd0af35c5 );	ROM_LOAD( "a78-14.17",    0x28000, 0x8000, 0x7b5369a8 );	/* 0x30000-0x3ffff empty */
		ROM_LOAD( "a78-15.30",    0x40000, 0x8000, 0x6b61a413 );   /* 2nd plane */
		ROM_LOAD( "a78-16.31",    0x48000, 0x8000, 0xb5492d97 );	ROM_LOAD( "a78-17.32",    0x50000, 0x8000, 0xd69762d5 );	ROM_LOAD( "a78-18.33",    0x58000, 0x8000, 0x9f243b68 );	ROM_LOAD( "a78-19.34",    0x60000, 0x8000, 0x66e9438c );	ROM_LOAD( "a78-20.35",    0x68000, 0x8000, 0x9ef863ad );	/* 0x70000-0x7ffff empty */
	
		ROM_REGION( 0x0100, REGION_PROMS, 0 );	ROM_LOAD( "a71-25.41",    0x0000, 0x0100, 0x2d0f8545 );/* video timing */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_bubbobr1 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );	ROM_LOAD( "a78-06.51",    0x00000, 0x08000, 0x32c8305b );    /* ROMs banked at 8000-bfff */
		ROM_LOAD( "a78-21.52",    0x10000, 0x10000, 0x2844033d );	/* 20000-2ffff empty */
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "a78-08.37",    0x0000, 0x08000, 0xae11a07b );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU */
		ROM_LOAD( "a78-07.46",    0x0000, 0x08000, 0x4f9a26e8 );
		ROM_REGION( 0x0800, REGION_CPU4, 0 );/* 2k for the microcontroller */
		ROM_LOAD( "68705.bin",    0x0000, 0x0800, 0x78caa635 );/* from a pirate board */
	
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );	ROM_LOAD( "a78-09.12",    0x00000, 0x8000, 0x20358c22 );   /* 1st plane */
		ROM_LOAD( "a78-10.13",    0x08000, 0x8000, 0x930168a9 );	ROM_LOAD( "a78-11.14",    0x10000, 0x8000, 0x9773e512 );	ROM_LOAD( "a78-12.15",    0x18000, 0x8000, 0xd045549b );	ROM_LOAD( "a78-13.16",    0x20000, 0x8000, 0xd0af35c5 );	ROM_LOAD( "a78-14.17",    0x28000, 0x8000, 0x7b5369a8 );	/* 0x30000-0x3ffff empty */
		ROM_LOAD( "a78-15.30",    0x40000, 0x8000, 0x6b61a413 );   /* 2nd plane */
		ROM_LOAD( "a78-16.31",    0x48000, 0x8000, 0xb5492d97 );	ROM_LOAD( "a78-17.32",    0x50000, 0x8000, 0xd69762d5 );	ROM_LOAD( "a78-18.33",    0x58000, 0x8000, 0x9f243b68 );	ROM_LOAD( "a78-19.34",    0x60000, 0x8000, 0x66e9438c );	ROM_LOAD( "a78-20.35",    0x68000, 0x8000, 0x9ef863ad );	/* 0x70000-0x7ffff empty */
	
		ROM_REGION( 0x0100, REGION_PROMS, 0 );	ROM_LOAD( "a71-25.41",    0x0000, 0x0100, 0x2d0f8545 );/* video timing */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_boblbobl = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );	ROM_LOAD( "bb3",          0x00000, 0x08000, 0x01f81936 );    /* ROMs banked at 8000-bfff */
		ROM_LOAD( "bb5",          0x10000, 0x08000, 0x13118eb1 );	ROM_LOAD( "bb4",          0x18000, 0x08000, 0xafda99d8 );	/* 20000-2ffff empty */
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "a78-08.37",    0x0000, 0x08000, 0xae11a07b );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU */
		ROM_LOAD( "a78-07.46",    0x0000, 0x08000, 0x4f9a26e8 );
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );	ROM_LOAD( "a78-09.12",    0x00000, 0x8000, 0x20358c22 );   /* 1st plane */
		ROM_LOAD( "a78-10.13",    0x08000, 0x8000, 0x930168a9 );	ROM_LOAD( "a78-11.14",    0x10000, 0x8000, 0x9773e512 );	ROM_LOAD( "a78-12.15",    0x18000, 0x8000, 0xd045549b );	ROM_LOAD( "a78-13.16",    0x20000, 0x8000, 0xd0af35c5 );	ROM_LOAD( "a78-14.17",    0x28000, 0x8000, 0x7b5369a8 );	/* 0x30000-0x3ffff empty */
		ROM_LOAD( "a78-15.30",    0x40000, 0x8000, 0x6b61a413 );   /* 2nd plane */
		ROM_LOAD( "a78-16.31",    0x48000, 0x8000, 0xb5492d97 );	ROM_LOAD( "a78-17.32",    0x50000, 0x8000, 0xd69762d5 );	ROM_LOAD( "a78-18.33",    0x58000, 0x8000, 0x9f243b68 );	ROM_LOAD( "a78-19.34",    0x60000, 0x8000, 0x66e9438c );	ROM_LOAD( "a78-20.35",    0x68000, 0x8000, 0x9ef863ad );	/* 0x70000-0x7ffff empty */
	
		ROM_REGION( 0x0100, REGION_PROMS, 0 );	ROM_LOAD( "a71-25.41",    0x0000, 0x0100, 0x2d0f8545 );/* video timing */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_sboblbob = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );	ROM_LOAD( "bbb-3.rom",    0x00000, 0x08000, 0xf304152a );    /* ROMs banked at 8000-bfff */
		ROM_LOAD( "bb5",          0x10000, 0x08000, 0x13118eb1 );	ROM_LOAD( "bbb-4.rom",    0x18000, 0x08000, 0x94c75591 );	/* 20000-2ffff empty */
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "a78-08.37",    0x0000, 0x08000, 0xae11a07b );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU */
		ROM_LOAD( "a78-07.46",    0x0000, 0x08000, 0x4f9a26e8 );
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );	ROM_LOAD( "a78-09.12",    0x00000, 0x8000, 0x20358c22 );   /* 1st plane */
		ROM_LOAD( "a78-10.13",    0x08000, 0x8000, 0x930168a9 );	ROM_LOAD( "a78-11.14",    0x10000, 0x8000, 0x9773e512 );	ROM_LOAD( "a78-12.15",    0x18000, 0x8000, 0xd045549b );	ROM_LOAD( "a78-13.16",    0x20000, 0x8000, 0xd0af35c5 );	ROM_LOAD( "a78-14.17",    0x28000, 0x8000, 0x7b5369a8 );	/* 0x30000-0x3ffff empty */
		ROM_LOAD( "a78-15.30",    0x40000, 0x8000, 0x6b61a413 );   /* 2nd plane */
		ROM_LOAD( "a78-16.31",    0x48000, 0x8000, 0xb5492d97 );	ROM_LOAD( "a78-17.32",    0x50000, 0x8000, 0xd69762d5 );	ROM_LOAD( "a78-18.33",    0x58000, 0x8000, 0x9f243b68 );	ROM_LOAD( "a78-19.34",    0x60000, 0x8000, 0x66e9438c );	ROM_LOAD( "a78-20.35",    0x68000, 0x8000, 0x9ef863ad );	/* 0x70000-0x7ffff empty */
	
		ROM_REGION( 0x0100, REGION_PROMS, 0 );	ROM_LOAD( "a71-25.41",    0x0000, 0x0100, 0x2d0f8545 );/* video timing */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_tokio = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* main CPU */
		ROM_LOAD( "a71-27-1.256", 0x00000, 0x8000, 0x8c180896 );    /* ROMs banked at 8000-bfff */
		ROM_LOAD( "a71-28-1.256", 0x10000, 0x8000, 0x1b447527 );	ROM_LOAD( "a71-04.256",   0x18000, 0x8000, 0xa0a4ce0e );	ROM_LOAD( "a71-05.256",   0x20000, 0x8000, 0x6da0b945 );	ROM_LOAD( "a71-06-1.256", 0x28000, 0x8000, 0x56927b3f );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* video CPU */
		ROM_LOAD( "a71-01.256",   0x00000, 0x8000, 0x0867c707 );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* audio CPU */
		ROM_LOAD( "a71-07.256",   0x0000, 0x08000, 0xf298cc7b );
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );	ROM_LOAD( "a71-08.256",   0x00000, 0x8000, 0x0439ab13 );   /* 1st plane */
		ROM_LOAD( "a71-09.256",   0x08000, 0x8000, 0xedb3d2ff );	ROM_LOAD( "a71-10.256",   0x10000, 0x8000, 0x69f0888c );	ROM_LOAD( "a71-11.256",   0x18000, 0x8000, 0x4ae07c31 );	ROM_LOAD( "a71-12.256",   0x20000, 0x8000, 0x3f6bd706 );	ROM_LOAD( "a71-13.256",   0x28000, 0x8000, 0xf2c92aaa );	ROM_LOAD( "a71-14.256",   0x30000, 0x8000, 0xc574b7b2 );	ROM_LOAD( "a71-15.256",   0x38000, 0x8000, 0x12d87e7f );	ROM_LOAD( "a71-16.256",   0x40000, 0x8000, 0x0bce35b6 );   /* 2nd plane */
		ROM_LOAD( "a71-17.256",   0x48000, 0x8000, 0xdeda6387 );	ROM_LOAD( "a71-18.256",   0x50000, 0x8000, 0x330cd9d7 );	ROM_LOAD( "a71-19.256",   0x58000, 0x8000, 0xfc4b29e0 );	ROM_LOAD( "a71-20.256",   0x60000, 0x8000, 0x65acb265 );	ROM_LOAD( "a71-21.256",   0x68000, 0x8000, 0x33cde9b2 );	ROM_LOAD( "a71-22.256",   0x70000, 0x8000, 0xfb98eac0 );	ROM_LOAD( "a71-23.256",   0x78000, 0x8000, 0x30bd46ad );
		ROM_REGION( 0x0100, REGION_PROMS, 0 );	ROM_LOAD( "a71-25.bin",   0x0000, 0x0100, 0x2d0f8545 );/* video timing */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_tokiob = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* main CPU */
		ROM_LOAD( "2",            0x00000, 0x8000, 0xf583b1ef );    /* ROMs banked at 8000-bfff */
		ROM_LOAD( "3",            0x10000, 0x8000, 0x69dacf44 );	ROM_LOAD( "a71-04.256",   0x18000, 0x8000, 0xa0a4ce0e );	ROM_LOAD( "a71-05.256",   0x20000, 0x8000, 0x6da0b945 );	ROM_LOAD( "6",            0x28000, 0x8000, 0x1490e95b );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* video CPU */
		ROM_LOAD( "a71-01.256",   0x00000, 0x8000, 0x0867c707 );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* audio CPU */
		ROM_LOAD( "a71-07.256",   0x0000, 0x08000, 0xf298cc7b );
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );	ROM_LOAD( "a71-08.256",   0x00000, 0x8000, 0x0439ab13 );   /* 1st plane */
		ROM_LOAD( "a71-09.256",   0x08000, 0x8000, 0xedb3d2ff );	ROM_LOAD( "a71-10.256",   0x10000, 0x8000, 0x69f0888c );	ROM_LOAD( "a71-11.256",   0x18000, 0x8000, 0x4ae07c31 );	ROM_LOAD( "a71-12.256",   0x20000, 0x8000, 0x3f6bd706 );	ROM_LOAD( "a71-13.256",   0x28000, 0x8000, 0xf2c92aaa );	ROM_LOAD( "a71-14.256",   0x30000, 0x8000, 0xc574b7b2 );	ROM_LOAD( "a71-15.256",   0x38000, 0x8000, 0x12d87e7f );	ROM_LOAD( "a71-16.256",   0x40000, 0x8000, 0x0bce35b6 );   /* 2nd plane */
		ROM_LOAD( "a71-17.256",   0x48000, 0x8000, 0xdeda6387 );	ROM_LOAD( "a71-18.256",   0x50000, 0x8000, 0x330cd9d7 );	ROM_LOAD( "a71-19.256",   0x58000, 0x8000, 0xfc4b29e0 );	ROM_LOAD( "a71-20.256",   0x60000, 0x8000, 0x65acb265 );	ROM_LOAD( "a71-21.256",   0x68000, 0x8000, 0x33cde9b2 );	ROM_LOAD( "a71-22.256",   0x70000, 0x8000, 0xfb98eac0 );	ROM_LOAD( "a71-23.256",   0x78000, 0x8000, 0x30bd46ad );
		ROM_REGION( 0x0100, REGION_PROMS, 0 );	ROM_LOAD( "a71-25.bin",   0x0000, 0x0100, 0x2d0f8545 );/* video timing */
	ROM_END(); }}; 
	
	
	
	public static InitDriverPtr init_bublbobl = new InitDriverPtr() { public void handler()
	{
		UBytePtr ROM = new UBytePtr(memory_region(REGION_CPU1));
	
		/* in Bubble Bobble, bank 0 has code falling from 7fff to 8000, */
		/* so I have to copy it there because bank switching wouldn't catch it */
		memcpy(new UBytePtr(ROM, 0x08000), new UBytePtr(ROM, 0x10000),0x4000);
	} };
	
        public static void MOD_PAGE(int page, int addr, int data){ 
            memory_region(REGION_CPU1).write(addr-0x8000+0x10000+0x4000*page, data);
        }
	
	public static InitDriverPtr init_boblbobl = new InitDriverPtr() { public void handler()
	{
	
	    /* these shouldn't be necessary, surely - this is a bootleg ROM
	     * with the protection removed - so what are all these JP's to
	     * 0xa288 doing?  and why does the emulator fail the ROM checks?
	     */
	
		MOD_PAGE(3,0x9a71,0x00); MOD_PAGE(3,0x9a72,0x00); MOD_PAGE(3,0x9a73,0x00);
		MOD_PAGE(3,0xa4af,0x00); MOD_PAGE(3,0xa4b0,0x00); MOD_PAGE(3,0xa4b1,0x00);
		MOD_PAGE(3,0xa55d,0x00); MOD_PAGE(3,0xa55e,0x00); MOD_PAGE(3,0xa55f,0x00);
		MOD_PAGE(3,0xb561,0x00); MOD_PAGE(3,0xb562,0x00); MOD_PAGE(3,0xb563,0x00);
	
		init_bublbobl.handler();
	} };
	
	
	public static InitDriverPtr init_tokio = new InitDriverPtr() { public void handler()
	{
		
		/* preemptively enable video, the bit is not mapped for this game and */
		/* I don't know if it even has it. */
		bublbobl_video_enable = 1;
	} };
	
	
	public static GameDriver driver_bublbobl	   = new GameDriver("1986"	,"bublbobl"	,"bublbobl.java"	,rom_bublbobl,null	,machine_driver_bublbobl	,input_ports_bublbobl	,init_bublbobl	,ROT0	,	"Taito Corporation", "Bubble Bobble" );
	public static GameDriver driver_bublbobr	   = new GameDriver("1986"	,"bublbobr"	,"bublbobl.java"	,rom_bublbobr,driver_bublbobl	,machine_driver_bublbobl	,input_ports_bublbobl	,init_bublbobl	,ROT0	,	"Taito America Corporation (Romstar license)", "Bubble Bobble (US with mode select)" );
	public static GameDriver driver_bubbobr1	   = new GameDriver("1986"	,"bubbobr1"	,"bublbobl.java"	,rom_bubbobr1,driver_bublbobl	,machine_driver_bublbobl	,input_ports_bublbobl	,init_bublbobl	,ROT0	,	"Taito America Corporation (Romstar license)", "Bubble Bobble (US)" );
	public static GameDriver driver_boblbobl	   = new GameDriver("1986"	,"boblbobl"	,"bublbobl.java"	,rom_boblbobl,driver_bublbobl	,machine_driver_boblbobl	,input_ports_boblbobl	,init_boblbobl	,ROT0	,	"bootleg", "Bobble Bobble" );
	public static GameDriver driver_sboblbob	   = new GameDriver("1986"	,"sboblbob"	,"bublbobl.java"	,rom_sboblbob,driver_bublbobl	,machine_driver_boblbobl	,input_ports_sboblbob	,init_bublbobl	,ROT0	,	"bootleg", "Super Bobble Bobble" );
	public static GameDriver driver_tokio	   = new GameDriver("1986"	,"tokio"	,"bublbobl.java"	,rom_tokio,null	,machine_driver_tokio	,input_ports_tokio	,init_tokio	,ROT90	,	"Taito", "Tokio / Scramble Formation", GAME_NOT_WORKING );
	public static GameDriver driver_tokiob	   = new GameDriver("1986"	,"tokiob"	,"bublbobl.java"	,rom_tokiob,driver_tokio	,machine_driver_tokio	,input_ports_tokio	,init_tokio	,ROT90	,	"bootleg", "Tokio / Scramble Formation (bootleg)" );
}
