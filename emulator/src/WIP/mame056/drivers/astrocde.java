/****************************************************************************

Bally Astrocade style games

driver by Nicola Salmoria, Mike Coates, Frank Palazzolo

TODO:
- support stereo sound in Gorf (and maybe others, Wow has 3 speakers)

Notes:
- In seawolf2, service mode dip switch turns on memory test. Reset with 2 pressed
  to get to an input check screen, reset with 1+2 pressed to get to a convergence
  test screen.


memory map (preliminary)

0000-3fff ROM but also "magic" RAM (which processes data and copies it to the video RAM)
4000-7fff SCREEN RAM (bitmap)
8000-cfff ROM
d000-d3ff STATIC RAM

I/O ports:
IN:
08        intercept register (collision detector)
	      bit 0: intercept in pixel 3 in an OR or XOR write since last reset
	      bit 1: intercept in pixel 2 in an OR or XOR write since last reset
	      bit 2: intercept in pixel 1 in an OR or XOR write since last reset
	      bit 3: intercept in pixel 0 in an OR or XOR write since last reset
	      bit 4: intercept in pixel 3 in last OR or XOR write
	      bit 5: intercept in pixel 2 in last OR or XOR write
	      bit 6: intercept in pixel 1 in last OR or XOR write
	      bit 7: intercept in pixel 0 in last OR or XOR write
10        IN0
11        IN1
12        IN2
13        DSW
14		  Video Retrace
15        ?
17        Speech Synthesizer (Output)

OUT:
00-07     palette (00-03 = right part of screen; 04-07 left part)
08        select video mode (0 = low res 160x102, 1 = high res 320x204)
09        --xxxxxx position where to switch from the "left" to the "right" palette (/2).
          xx------ background color (portion of screen after vblank)
0a        screen height
0b        color block transfer
0c        magic RAM control
	      bit 7: ?
	      bit 6: flip
	      bit 5: draw in XOR mode
	      bit 4: draw in OR mode
	      bit 3: "expand" mode (convert 1bpp data to 2bpp)
	      bit 2: "rotate" mode (rotate 90 degrees - NOT EMULATED)
	      bit 1: shift amount to be applied before copying
	      bit 0:/
0d        set interrupt vector
10-18     sound
19        magic RAM expand mode color
78-7e     pattern board (see vidhrdw.c for details)

****************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.vidhrdw.astrocde.*;
import static WIP.mame056.machine.astrocde.*;
import static WIP.mame056.sndhrdw.astrocde.*;
import static WIP.mame056.sndhrdw.gorf.*;

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
import static WIP2.mame056.memory.*;
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
import static WIP.mame056.sound.astrocde.*;
import static WIP.mame056.sound.astrocdeH.*;


import static WIP2.mame056.sound._2203intf.*;
import static WIP2.mame056.sound._2203intfH.*;
import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.palette.*;

public class astrocde
{
	
	public static WriteHandlerPtr seawolf2_lamps_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/* 0x42 = player 2 (left), 0x43 = player 1 (right) */
		/* --x----- explosion */
		/* ---x---- RELOAD (active low) */
		/* ----x--- torpedo 1 available */
		/* -----x-- torpedo 2 available */
		/* ------x- torpedo 3 available */
		/* -------x torpedo 4 available */
	
		/* I'm only supporting the "RELOAD" lamp since we don't have enough leds ;-) */
		/*TODO*///set_led_status(offset^1,data & 0x10);
	} };
	
	
	public static Memory_ReadAddress seawolf2_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x1fff, MRA_ROM ),
		new Memory_ReadAddress( 0x4000, 0x7fff, MRA_RAM ),
		new Memory_ReadAddress( 0xc000, 0xc3ff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress seawolf2_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x3fff, wow_magicram_w ),
		new Memory_WriteAddress( 0x4000, 0x7fff, wow_videoram_w, wow_videoram, videoram_size ),
		new Memory_WriteAddress( 0xc000, 0xc3ff, MWA_RAM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x4000, 0x7fff, MRA_RAM ),
		new Memory_ReadAddress( 0x8000, 0xcfff, MRA_ROM ),
		new Memory_ReadAddress( 0xd000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x3fff, wow_magicram_w ),
		new Memory_WriteAddress( 0x4000, 0x7fff, wow_videoram_w, wow_videoram, videoram_size ),	/* ASG */
		new Memory_WriteAddress( 0x8000, 0xcfff, MWA_ROM ),
		new Memory_WriteAddress( 0xd000, 0xdfff, MWA_RAM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress robby_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x4000, 0x7fff, MRA_RAM ),
		new Memory_ReadAddress( 0x8000, 0xdfff, MRA_ROM ),
		new Memory_ReadAddress( 0xe000, 0xffff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress robby_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x3fff, wow_magicram_w ),
		new Memory_WriteAddress( 0x4000, 0x7fff, wow_videoram_w, wow_videoram, videoram_size ),
		new Memory_WriteAddress( 0x8000, 0xdfff, MWA_ROM ),
		new Memory_WriteAddress( 0xe000, 0xffff, MWA_RAM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress profpac_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0xdfff, MRA_ROM ),
		new Memory_ReadAddress( 0xe000, 0xffff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress profpac_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x3fff, wow_magicram_w ),
		new Memory_WriteAddress( 0x4000, 0x7fff, wow_videoram_w, wow_videoram, videoram_size ),
		new Memory_WriteAddress( 0x8000, 0xdfff, MWA_ROM ),
		new Memory_WriteAddress( 0xe000, 0xffff, MWA_RAM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static IO_ReadPort readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_ReadPort( 0x08, 0x08, wow_intercept_r ),
		new IO_ReadPort( 0x0e, 0x0e, wow_video_retrace_r ),
		new IO_ReadPort( 0x10, 0x10, input_port_0_r ),
		new IO_ReadPort( 0x11, 0x11, input_port_1_r ),
	  	new IO_ReadPort( 0x12, 0x12, input_port_2_r ),
		new IO_ReadPort( 0x13, 0x13, input_port_3_r ),
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort seawolf2_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( 0x00, 0x07, astrocde_colour_register_w ),
		new IO_WritePort( 0x08, 0x08, astrocde_mode_w ),
		new IO_WritePort( 0x09, 0x09, astrocde_colour_split_w ),
		new IO_WritePort( 0x0a, 0x0a, astrocde_vertical_blank_w ),
		new IO_WritePort( 0x0b, 0x0b, astrocde_colour_block_w ),
		new IO_WritePort( 0x0c, 0x0c, astrocde_magic_control_w ),
		new IO_WritePort( 0x0d, 0x0d, interrupt_vector_w ),
		new IO_WritePort( 0x0e, 0x0e, astrocde_interrupt_enable_w ),
		new IO_WritePort( 0x0f, 0x0f, astrocde_interrupt_w ),
		new IO_WritePort( 0x19, 0x19, astrocde_magic_expand_color_w ),
		new IO_WritePort( 0x40, 0x41, MWA_NOP ), /* analog sound */
		new IO_WritePort( 0x42, 0x43, seawolf2_lamps_w ),	/* cabinet lamps */
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( 0x00, 0x07, astrocde_colour_register_w ),
		new IO_WritePort( 0x08, 0x08, astrocde_mode_w ),
		new IO_WritePort( 0x09, 0x09, astrocde_colour_split_w ),
		new IO_WritePort( 0x0a, 0x0a, astrocde_vertical_blank_w ),
		new IO_WritePort( 0x0b, 0x0b, astrocde_colour_block_w ),
		new IO_WritePort( 0x0c, 0x0c, astrocde_magic_control_w ),
		new IO_WritePort( 0x0d, 0x0d, interrupt_vector_w ),
		new IO_WritePort( 0x0e, 0x0e, astrocde_interrupt_enable_w ),
		new IO_WritePort( 0x0f, 0x0f, astrocde_interrupt_w ),
		new IO_WritePort( 0x10, 0x18, astrocade_sound1_w ),
		new IO_WritePort( 0x19, 0x19, astrocde_magic_expand_color_w ),
		new IO_WritePort( 0x50, 0x58, astrocade_sound2_w ),
		new IO_WritePort( 0x5b, 0x5b, MWA_NOP ), /* speech board ? Wow always sets this to a5*/
		new IO_WritePort( 0x78, 0x7e, astrocde_pattern_board_w ),
	/*	new IO_WritePort( 0xf8, 0xff, MWA_NOP ), */ /* Gorf uses these */
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	
	
	static InputPortPtr input_ports_seawolf2 = new InputPortPtr(){ public void handler() { 
		PORT_START();  /* IN0 */
		PORT_ANALOG( 0x3f, 0x20, IPT_PADDLE | IPF_REVERSE | IPF_PLAYER1, 20, 5, 0, 0x3f);
		PORT_DIPNAME( 0x40, 0x00, "Language 1" );
		PORT_DIPSETTING(    0x00, "Language 2" );
		PORT_DIPSETTING(    0x40, "French" );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER1 );
	
		PORT_START();  /* IN1 */
		PORT_ANALOG( 0x3f, 0x20, IPT_PADDLE | IPF_REVERSE | IPF_PLAYER2, 20, 5, 0, 0x3f);
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER2 );
	
		PORT_START();  /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_START1 );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_START2 );
		PORT_DIPNAME( 0x08, 0x00, "Language 2" );
		PORT_DIPSETTING(    0x00, "English" );
		PORT_DIPSETTING(    0x08, "German" );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
	
		PORT_START();  /* Dip Switch */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0x06, 0x00, "Play Time" );
		PORT_DIPSETTING(    0x06, "40" );
		PORT_DIPSETTING(    0x04, "50" );
		PORT_DIPSETTING(    0x02, "60" );
		PORT_DIPSETTING(    0x00, "70" );
		PORT_DIPNAME( 0x08, 0x08, "2 Players Game" );
		PORT_DIPSETTING(    0x00, "1 Credit" );
		PORT_DIPSETTING(    0x08, "2 Credits" );
		PORT_DIPNAME( 0x30, 0x00, "Extended Play" );
		PORT_DIPSETTING(    0x10, "5000" );
		PORT_DIPSETTING(    0x20, "6000" );
		PORT_DIPSETTING(    0x30, "7000" );
		PORT_DIPSETTING(    0x00, "None" );
		PORT_DIPNAME( 0x40, 0x40, "Monitor" );
		PORT_DIPSETTING(    0x40, "Color" );
		PORT_DIPSETTING(    0x00, "B/W" );
		PORT_SERVICE( 0x80, IP_ACTIVE_LOW );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_spacezap = new InputPortPtr(){ public void handler() { 
		PORT_START();  /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_TILT );
		PORT_SERVICE( 0x08, IP_ACTIVE_LOW );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* IN1 */
	
		PORT_START();  /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* Dip Switch */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0x06, 0x06, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x04, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_5C") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_ebases = new InputPortPtr(){ public void handler() { 
		PORT_START();  /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x0c, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_TILT );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, "Monitor" );
		PORT_DIPSETTING(    0x00, "Color" );
		PORT_DIPSETTING(    0x10, "B/W" );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	
		PORT_START();  /* Dip Switch */
		PORT_DIPNAME( 0x01, 0x00, "2 Players Game" );
		PORT_DIPSETTING(    0x00, "1 Credit" );
		PORT_DIPSETTING(    0x01, "2 Credits" );
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	
		PORT_START(); 
		PORT_ANALOGX( 0xff, 0x00, IPT_TRACKBALL_X | IPF_PLAYER2 | IPF_CENTER, 50, 10, 0, 0, IP_KEY_NONE, IP_KEY_NONE, IP_JOY_NONE, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_ANALOGX( 0xff, 0x00, IPT_TRACKBALL_Y | IPF_PLAYER2 | IPF_CENTER, 50, 10, 0, 0, IP_KEY_NONE, IP_KEY_NONE, IP_JOY_NONE, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_ANALOGX( 0xff, 0x00, IPT_TRACKBALL_X | IPF_CENTER, 50, 10, 0, 0, IP_KEY_NONE, IP_KEY_NONE, IP_JOY_NONE, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_ANALOGX( 0xff, 0x00, IPT_TRACKBALL_Y | IPF_CENTER, 50, 10, 0, 0, IP_KEY_NONE, IP_KEY_NONE, IP_JOY_NONE, IP_JOY_NONE );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_wow = new InputPortPtr(){ public void handler() { 
		PORT_START();  /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_COIN3 );
		PORT_SERVICE( 0x08, IP_ACTIVE_LOW );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();  /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );/* speech status */
	
		PORT_START();  /* Dip Switch */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0x06, 0x06, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x04, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_5C") );
		PORT_DIPNAME( 0x08, 0x08, "Language" );
		PORT_DIPSETTING(    0x08, "English" );
		PORT_DIPSETTING(    0x00, "Foreign (NEED ROM)" );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x10, "2 / 5" );
		PORT_DIPSETTING(    0x00, "3 / 7" );
		PORT_DIPNAME( 0x20, 0x20, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x20, "3rd Level" );
		PORT_DIPSETTING(    0x00, "4th Level" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Free_Play") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_gorf = new InputPortPtr(){ public void handler() { 
		PORT_START();  /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START2 );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );/* speech status */
	
		PORT_START();  /* Dip Switch */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0x06, 0x06, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x04, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_5C") );
		PORT_DIPNAME( 0x08, 0x08, "Language" );
		PORT_DIPSETTING(    0x08, "English" );
		PORT_DIPSETTING(    0x00, "Foreign (NEED ROM)" );
		PORT_DIPNAME( 0x10, 0x00, "Lives per Credit" );
		PORT_DIPSETTING(    0x10, "2" );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "Mission 5" );
		PORT_DIPSETTING(    0x20, "None" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Free_Play") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_robby = new InputPortPtr(){ public void handler() { 
		PORT_START();  /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_SERVICE( 0x08, IP_ACTIVE_LOW );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();  /* Dip Switch */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x01, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Free_Play") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	INPUT_PORTS_END(); }}; 
	
	
	
	static Samplesinterface wow_samples_interface = new Samplesinterface
        (
		8,	/* 8 channels */
		25,	/* volume */
		wow_sample_names
        );
	
	static Samplesinterface gorf_samples_interface = new Samplesinterface
        (
		8,	/* 8 channels */
		25,	/* volume */
		gorf_sample_names
        );
	
	static astrocade_interface astrocade_2chip_interface = new astrocade_interface
	(
		2,			/* Number of chips */
		1789773,	/* Clock speed */
		new int[]{255,255}			/* Volume */
	);
	
	static astrocade_interface astrocade_1chip_interface = new astrocade_interface
	(
		1,			/* Number of chips */
		1789773,	/* Clock speed */
		new int[]{255}			/* Volume */
	);
	
	static CustomSound_interface gorf_custom_interface = new CustomSound_interface
	(
		gorf_sh_start,
		null,
		gorf_sh_update
	);
	
	static CustomSound_interface wow_custom_interface = new CustomSound_interface
	(
		wow_sh_start,
		null,
		wow_sh_update
	);
	
	
	
	
	static MachineDriver machine_driver_seawolf2 = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1789773,	/* 1.789 MHz */
				seawolf2_readmem,seawolf2_writemem,readport,seawolf2_writeport,
				wow_interrupt,256
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		320, 204, new rectangle( 0, 320-1, 0, 204-1 ),
		null,	/* no gfxdecodeinfo - bitmapped display */
		256, 0,
		astrocde_init_palette,
	
		VIDEO_TYPE_RASTER,
		null,
		astrocde_vh_start,
		astrocde_vh_stop,
		seawolf2_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
                
                null
	);
	
	static MachineDriver machine_driver_spacezap = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1789773,	/* 1.789 MHz */
				readmem,writemem,readport,writeport,
				wow_interrupt,256
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		320, 204, new rectangle( 0, 320-1, 0, 204-1 ),
		null,	/* no gfxdecodeinfo - bitmapped display */
		256, 0,
		astrocde_init_palette,
	
		VIDEO_TYPE_RASTER,
		null,
		astrocde_vh_start,
		astrocde_vh_stop,
		astrocde_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_ASTROCADE,
				astrocade_2chip_interface
			)
		}
                
	);
	
	static MachineDriver machine_driver_ebases = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1789773,	/* 1.789 MHz */
				readmem,writemem,readport,writeport,
				wow_interrupt,256
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		320, 204, new rectangle( 0, 320-1, 0, 204-1 ),
		null,	/* no gfxdecodeinfo - bitmapped display */
		256, 0,
		astrocde_init_palette,
	
		VIDEO_TYPE_RASTER,
		null,
		astrocde_vh_start,
		astrocde_vh_stop,
		astrocde_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_ASTROCADE,
				astrocade_1chip_interface
			)
		}
                
	);
	
	static MachineDriver machine_driver_wow = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1789773,	/* 1.789 MHz */
				readmem,writemem,readport,writeport,
				wow_interrupt,256
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		320, 204, new rectangle( 0, 320-1, 0, 204-1 ),
		null,	/* no gfxdecodeinfo - bitmapped display */
		256, 0,
		astrocde_init_palette,
	
		VIDEO_TYPE_RASTER,
		null,
		astrocde_stars_vh_start,
		astrocde_vh_stop,
		astrocde_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_ASTROCADE,
				astrocade_2chip_interface
			),
			new MachineSound(
				SOUND_SAMPLES,
				wow_samples_interface
			),
			new MachineSound(
				SOUND_CUSTOM,	/* actually plays the samples */
				wow_custom_interface
			)
	 	}
                
	);
	
	static MachineDriver machine_driver_gorf = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1789773,	/* 1.789 MHz */
				readmem,writemem,readport,writeport,
				gorf_interrupt,256
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		/* it may look like the right hand side of the screen needs clipping, but */
		/* this isn't the case: cocktail mode would be clipped on the wrong side */
	
		320, 204, new rectangle( 0, 320-1, 0, 204-1 ),
		null,	/* no gfxdecodeinfo - bitmapped display */
		256, 0,
		astrocde_init_palette,
	
		VIDEO_TYPE_RASTER,
		null,
		astrocde_stars_vh_start,
		astrocde_vh_stop,
		astrocde_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_ASTROCADE,
				astrocade_2chip_interface
			),
			new MachineSound(
				SOUND_SAMPLES,
				gorf_samples_interface
			),
			new MachineSound(
				SOUND_CUSTOM,	/* actually plays the samples */
				gorf_custom_interface
			)
		}
                
               
	);
	
	static MachineDriver machine_driver_robby = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1789773,	/* 1.789 MHz */
				robby_readmem,robby_writemem,readport,writeport,
				wow_interrupt,256
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		320, 204, new rectangle( 0, 320-1, 0, 204-1 ),
		null,	/* no gfxdecodeinfo - bitmapped display */
		256, 0,
		astrocde_init_palette,
	
		VIDEO_TYPE_RASTER,
		null,
		astrocde_vh_start,
		astrocde_vh_stop,
		astrocde_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_ASTROCADE,
				astrocade_2chip_interface
			)
		}
                
                
	);
	
	static MachineDriver machine_driver_profpac = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1789773,	/* 1.789 MHz */
				profpac_readmem,profpac_writemem,readport,writeport,
				wow_interrupt,256
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		320, 204, new rectangle( 0, 320-1, 0, 204-1 ),
		null,	/* no gfxdecodeinfo - bitmapped display */
		256, 0,
		astrocde_init_palette,
	
		VIDEO_TYPE_RASTER,
		null,
		astrocde_vh_start,
		astrocde_vh_stop,
		astrocde_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_ASTROCADE,
				astrocade_2chip_interface
			)
		}

	);
	
	
	
	static RomLoadPtr rom_seawolf2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "sw2x1.bin",    0x0000, 0x0800, 0xad0103f6 );
		ROM_LOAD( "sw2x2.bin",    0x0800, 0x0800, 0xe0430f0a );
		ROM_LOAD( "sw2x3.bin",    0x1000, 0x0800, 0x05ad1619 );
		ROM_LOAD( "sw2x4.bin",    0x1800, 0x0800, 0x1a1a14a2 );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_spacezap = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "0662.01",      0x0000, 0x1000, 0xa92de312 );
		ROM_LOAD( "0663.xx",      0x1000, 0x1000, 0x4836ebf1 );
		ROM_LOAD( "0664.xx",      0x2000, 0x1000, 0xd8193a80 );
		ROM_LOAD( "0665.xx",      0x3000, 0x1000, 0x3784228d );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_ebases = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "m761a",        0x0000, 0x1000, 0x34422147 );
		ROM_LOAD( "m761b",        0x1000, 0x1000, 0x4f28dfd6 );
		ROM_LOAD( "m761c",        0x2000, 0x1000, 0xbff6c97e );
		ROM_LOAD( "m761d",        0x3000, 0x1000, 0x5173781a );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_wow = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "wow.x1",       0x0000, 0x1000, 0xc1295786 );
		ROM_LOAD( "wow.x2",       0x1000, 0x1000, 0x9be93215 );
		ROM_LOAD( "wow.x3",       0x2000, 0x1000, 0x75e5a22e );
		ROM_LOAD( "wow.x4",       0x3000, 0x1000, 0xef28eb84 );
		ROM_LOAD( "wow.x5",       0x8000, 0x1000, 0x16912c2b );
		ROM_LOAD( "wow.x6",       0x9000, 0x1000, 0x35797f82 );
		ROM_LOAD( "wow.x7",       0xa000, 0x1000, 0xce404305 );
	/*	ROM_LOAD( "wow.x8",       0xc000, 0x1000, ? );here would go the foreign language ROM */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_gorf = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "gorf-a.bin",   0x0000, 0x1000, 0x5b348321 );
		ROM_LOAD( "gorf-b.bin",   0x1000, 0x1000, 0x62d6de77 );
		ROM_LOAD( "gorf-c.bin",   0x2000, 0x1000, 0x1d3bc9c9 );
		ROM_LOAD( "gorf-d.bin",   0x3000, 0x1000, 0x70046e56 );
		ROM_LOAD( "gorf-e.bin",   0x8000, 0x1000, 0x2d456eb5 );
		ROM_LOAD( "gorf-f.bin",   0x9000, 0x1000, 0xf7e4e155 );
		ROM_LOAD( "gorf-g.bin",   0xa000, 0x1000, 0x4e2bd9b9 );
		ROM_LOAD( "gorf-h.bin",   0xb000, 0x1000, 0xfe7b863d );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_gorfpgm1 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "873a",         0x0000, 0x1000, 0x97cb4a6a );
		ROM_LOAD( "873b",         0x1000, 0x1000, 0x257236f8 );
		ROM_LOAD( "873c",         0x2000, 0x1000, 0x16b0638b );
		ROM_LOAD( "873d",         0x3000, 0x1000, 0xb5e821dc );
		ROM_LOAD( "873e",         0x8000, 0x1000, 0x8e82804b );
		ROM_LOAD( "873f",         0x9000, 0x1000, 0x715fb4d9 );
		ROM_LOAD( "873g",         0xa000, 0x1000, 0x8a066456 );
		ROM_LOAD( "873h",         0xb000, 0x1000, 0x56d40c7c );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_robby = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "rotox1.bin",   0x0000, 0x1000, 0xa431b85a );
		ROM_LOAD( "rotox2.bin",   0x1000, 0x1000, 0x33cdda83 );
		ROM_LOAD( "rotox3.bin",   0x2000, 0x1000, 0xdbf97491 );
		ROM_LOAD( "rotox4.bin",   0x3000, 0x1000, 0xa3b90ac8 );
		ROM_LOAD( "rotox5.bin",   0x8000, 0x1000, 0x46ae8a94 );
		ROM_LOAD( "rotox6.bin",   0x9000, 0x1000, 0x7916b730 );
		ROM_LOAD( "rotox7.bin",   0xa000, 0x1000, 0x276dc4a5 );
		ROM_LOAD( "rotox8.bin",   0xb000, 0x1000, 0x1ef13457 );
	  	ROM_LOAD( "rotox9.bin",   0xc000, 0x1000, 0x370352bf );
		ROM_LOAD( "rotox10.bin",  0xd000, 0x1000, 0xe762cbda );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_profpac = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "pps1",         0x0000, 0x2000, 0xa244a62d );
		ROM_LOAD( "pps2",         0x2000, 0x2000, 0x8a9a6653 );
		ROM_LOAD( "pps7",         0x8000, 0x2000, 0xf9c26aba );
		ROM_LOAD( "pps8",         0xa000, 0x2000, 0x4d201e41 );
		ROM_LOAD( "pps9",         0xc000, 0x2000, 0x17a0b418 );
	
		ROM_REGION( 0x04000, REGION_USER1, 0 );
		ROM_LOAD( "pps3",         0x0000, 0x2000, 0x15717fd8 );
		ROM_LOAD( "pps4",         0x0000, 0x2000, 0x36540598 );
		ROM_LOAD( "pps5",         0x0000, 0x2000, 0x8dc89a59 );
		ROM_LOAD( "pps6",         0x0000, 0x2000, 0x5a2186c3 );
		ROM_LOAD( "ppq1",         0x0000, 0x4000, 0xdddc2ccc );
		ROM_LOAD( "ppq2",         0x0000, 0x4000, 0x33bbcabe );
		ROM_LOAD( "ppq3",         0x0000, 0x4000, 0x3534d895 );
		ROM_LOAD( "ppq4",         0x0000, 0x4000, 0x17e3581d );
		ROM_LOAD( "ppq5",         0x0000, 0x4000, 0x80882a93 );
		ROM_LOAD( "ppq6",         0x0000, 0x4000, 0xe5ddaee5 );
		ROM_LOAD( "ppq7",         0x0000, 0x4000, 0xc029cd34 );
		ROM_LOAD( "ppq8",         0x0000, 0x4000, 0xfb3a1ac9 );
		ROM_LOAD( "ppq9",         0x0000, 0x4000, 0x5e944488 );
		ROM_LOAD( "ppq10",        0x0000, 0x4000, 0xed72a81f );
		ROM_LOAD( "ppq11",        0x0000, 0x4000, 0x98295020 );
		ROM_LOAD( "ppq12",        0x0000, 0x4000, 0xe01a8dbe );
		ROM_LOAD( "ppq13",        0x0000, 0x4000, 0x87165d4f );
		ROM_LOAD( "ppq14",        0x0000, 0x4000, 0xecb861de );
	ROM_END(); }}; 
	
	
	
	public static InitDriverPtr init_seawolf2 = new InitDriverPtr() { public void handler()
	{
		install_port_read_handler(0, 0x10, 0x10, seawolf2_controller2_r);
		install_port_read_handler(0, 0x11, 0x11, seawolf2_controller1_r);
	} };
	public static InitDriverPtr init_ebases = new InitDriverPtr() { public void handler()
	{
		install_port_read_handler (0, 0x13, 0x13, ebases_trackball_r);
		install_port_write_handler(0, 0x28, 0x28, ebases_trackball_select_w);
	} };
	public static InitDriverPtr init_wow = new InitDriverPtr() { public void handler()
	{
		install_port_read_handler(0, 0x12, 0x12, wow_port_2_r);
		install_port_read_handler(0, 0x15, 0x15, wow_io_r);
		install_port_read_handler(0, 0x17, 0x17, wow_speech_r);
	} };
	public static InitDriverPtr init_gorf = new InitDriverPtr() { public void handler()
	{
		install_mem_read_handler (0, 0xd0a5, 0xd0a5, gorf_timer_r);
	
		install_port_read_handler(0, 0x12, 0x12, gorf_port_2_r);
		install_port_read_handler(0, 0x15, 0x16, gorf_io_r);
		install_port_read_handler(0, 0x17, 0x17, gorf_speech_r);
	} };
	
	
	public static GameDriver driver_seawolf2	   = new GameDriver("1978"	,"seawolf2"	,"astrocde.java"	,rom_seawolf2,null	,machine_driver_seawolf2	,input_ports_seawolf2	,init_seawolf2	,ROT0	,	"Midway", "Sea Wolf II", GAME_NO_SOUND );
	public static GameDriver driver_spacezap	   = new GameDriver("1980"	,"spacezap"	,"astrocde.java"	,rom_spacezap,null	,machine_driver_spacezap	,input_ports_spacezap	,null	,ROT0	,	"Midway", "Space Zap" );
	public static GameDriver driver_ebases	   = new GameDriver("1980"	,"ebases"	,"astrocde.java"	,rom_ebases,null	,machine_driver_ebases	,input_ports_ebases	,init_ebases	,ROT0	,	"Midway", "Extra Bases" );
	public static GameDriver driver_wow	   = new GameDriver("1980"	,"wow"	,"astrocde.java"	,rom_wow,null	,machine_driver_wow	,input_ports_wow	,init_wow	,ROT0	,	"Midway", "Wizard of Wor" );
	public static GameDriver driver_gorf	   = new GameDriver("1981"	,"gorf"	,"astrocde.java"	,rom_gorf,null	,machine_driver_gorf	,input_ports_gorf	,init_gorf	,ROT270	,	"Midway", "Gorf" );
	public static GameDriver driver_gorfpgm1	   = new GameDriver("1981"	,"gorfpgm1"	,"astrocde.java"	,rom_gorfpgm1,driver_gorf	,machine_driver_gorf	,input_ports_gorf	,init_gorf	,ROT270	,	"Midway", "Gorf (Program 1)" );
	public static GameDriver driver_robby	   = new GameDriver("1981"	,"robby"	,"astrocde.java"	,rom_robby,null	,machine_driver_robby	,input_ports_robby	,null	,ROT0	,	"Bally Midway", "Robby Roto" );
	public static GameDriver driver_profpac	   = new GameDriver("1983"	,"profpac"	,"astrocde.java"	,rom_profpac,null	,machine_driver_profpac	,input_ports_gorf	,null	,ROT0	,	"Bally Midway", "Professor PacMan" );
}
