/***************************************************************************

Bosconian

driver by Martin Scragg


CPU #1:
0000-3fff ROM
CPU #2:
0000-1fff ROM
CPU #3:
0000-1fff ROM
ALL CPUS:
8000-83ff Video RAM
8400-87ff Color RAM
8b80-8bff sprite code/color
9380-93ff sprite position
9b80-9bff sprite control
8800-9fff RAM

read:
6800-6807 dip switches (only bits 0 and 1 are used - bit 0 is DSW1, bit 1 is DSW2)
	  dsw1:
	    bit 6-7 lives
	    bit 3-5 bonus
	    bit 0-2 coins per play
		  dsw2: (bootleg version, the original version is slightly different)
		    bit 7 cocktail/upright (1 = upright)
	    bit 6 ?
	    bit 5 RACK TEST
	    bit 4 pause (0 = paused, 1 = not paused)
	    bit 3 ?
	    bit 2 ?
	    bit 0-1 difficulty
7000-	  custom IO chip return values
7100	  custom IO chip status ($10 = command executed)

write:
6805	  sound voice 1 waveform (nibble)
6811-6813 sound voice 1 frequency (nibble)
6815	  sound voice 1 volume (nibble)
680a	  sound voice 2 waveform (nibble)
6816-6818 sound voice 2 frequency (nibble)
681a	  sound voice 2 volume (nibble)
680f	  sound voice 3 waveform (nibble)
681b-681d sound voice 3 frequency (nibble)
681f	  sound voice 3 volume (nibble)
6820	  cpu #1 irq acknowledge/enable
6821	  cpu #2 irq acknowledge/enable
6822	  cpu #3 nmi acknowledge/enable
6823	  if 0, halt CPU #2 and #3
6830	  Watchdog reset?
7000-	  custom IO chip parameters
7100	  custom IO chip command (see machine/bosco.c for more details)
a000-a002 starfield scroll direction/speed (only bit 0 is significant)
a003-a005 starfield blink?
a007	  flip screen

Interrupts:
CPU #1 IRQ mode 1
       NMI is triggered by the custom IO chip to signal the CPU to read/write
	       parameters
CPU #2 IRQ mode 1
CPU #3 NMI (@120Hz)

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
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;

import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.machine.bosco.*;
import static mame056.palette.*;
import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.vidhrdw.bosco.*;
import static WIP2.mame056.machine.bosco.*;
import static WIP2.mame056.sndhrdw.bosco.*;
import static WIP2.mame056.sound.namcoH.*;

public class bosco
{
	
	public static Memory_ReadAddress readmem_cpu1[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x6800, 0x6807, bosco_dsw_r ),
		new Memory_ReadAddress( 0x7000, 0x700f, bosco_customio_data_1_r ),
		new Memory_ReadAddress( 0x7100, 0x7100, bosco_customio_1_r ),
		new Memory_ReadAddress( 0x7800, 0x97ff, bosco_sharedram_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress readmem_cpu2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x1fff, MRA_ROM ),
		new Memory_ReadAddress( 0x6800, 0x6807, bosco_dsw_r ),
		new Memory_ReadAddress( 0x9000, 0x900f, bosco_customio_data_2_r ),
		new Memory_ReadAddress( 0x9100, 0x9100, bosco_customio_2_r ),
		new Memory_ReadAddress( 0x7800, 0x97ff, bosco_sharedram_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress readmem_cpu3[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x1fff, MRA_ROM ),
		new Memory_ReadAddress( 0x6800, 0x6807, bosco_dsw_r ),
		new Memory_ReadAddress( 0x7800, 0x97ff, bosco_sharedram_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem_cpu1[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x3fff, MWA_ROM ),
		new Memory_WriteAddress( 0x6800, 0x681f, pengo_sound_w, namco_soundregs ),
		new Memory_WriteAddress( 0x6820, 0x6820, bosco_interrupt_enable_1_w ),
		new Memory_WriteAddress( 0x6822, 0x6822, bosco_interrupt_enable_3_w ),
		new Memory_WriteAddress( 0x6823, 0x6823, bosco_halt_w ),
		new Memory_WriteAddress( 0x6830, 0x6830, watchdog_reset_w ),
		new Memory_WriteAddress( 0x7000, 0x700f, bosco_customio_data_1_w ),
		new Memory_WriteAddress( 0x7100, 0x7100, bosco_customio_1_w ),
	
		new Memory_WriteAddress( 0x8000, 0x83ff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x8400, 0x87ff, bosco_videoram2_w, bosco_videoram2 ),
		new Memory_WriteAddress( 0x8800, 0x8bff, colorram_w, colorram ),
		new Memory_WriteAddress( 0x8c00, 0x8fff, bosco_colorram2_w, bosco_colorram2 ),
	
		new Memory_WriteAddress( 0x7800, 0x97ff, bosco_sharedram_w, bosco_sharedram ),
	
		new Memory_WriteAddress( 0x83d4, 0x83df, MWA_RAM, spriteram, spriteram_size ),	/* these are here just to initialize */
		new Memory_WriteAddress( 0x8bd4, 0x8bdf, MWA_RAM, spriteram_2 ),			/* the pointers. */
		new Memory_WriteAddress( 0x83f4, 0x83ff, MWA_RAM, bosco_radarx, bosco_radarram_size ),	/* ditto */
		new Memory_WriteAddress( 0x8bf4, 0x8bff, MWA_RAM, bosco_radary ),
	
		new Memory_WriteAddress( 0x9810, 0x9810, bosco_scrollx_w ),
		new Memory_WriteAddress( 0x9820, 0x9820, bosco_scrolly_w ),
		new Memory_WriteAddress( 0x9830, 0x9830, bosco_starcontrol_w ),
		new Memory_WriteAddress( 0x9840, 0x9840, MWA_RAM, bosco_staronoff ),
		new Memory_WriteAddress( 0x9870, 0x9870, bosco_flipscreen_w ),
		new Memory_WriteAddress( 0x9804, 0x980f, MWA_RAM, bosco_radarattr ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem_cpu2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x1fff, MWA_ROM ),
		new Memory_WriteAddress( 0x6821, 0x6821, bosco_interrupt_enable_2_w ),
	
		new Memory_WriteAddress( 0x8000, 0x83ff, videoram_w ),
		new Memory_WriteAddress( 0x8400, 0x87ff, bosco_videoram2_w ),
		new Memory_WriteAddress( 0x8800, 0x8bff, colorram_w ),
		new Memory_WriteAddress( 0x8c00, 0x8fff, bosco_colorram2_w ),
		new Memory_WriteAddress( 0x9000, 0x900f, bosco_customio_data_2_w ),
		new Memory_WriteAddress( 0x9100, 0x9100, bosco_customio_2_w ),
		new Memory_WriteAddress( 0x7800, 0x97ff, bosco_sharedram_w ),
	
		new Memory_WriteAddress( 0x9810, 0x9810, bosco_scrollx_w ),
		new Memory_WriteAddress( 0x9820, 0x9820, bosco_scrolly_w ),
		new Memory_WriteAddress( 0x9830, 0x9830, bosco_starcontrol_w ),
		new Memory_WriteAddress( 0x9874, 0x9875, MWA_RAM, bosco_starblink ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem_cpu3[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x1fff, MWA_ROM ),
		new Memory_WriteAddress( 0x6800, 0x681f, pengo_sound_w ),
		new Memory_WriteAddress( 0x6822, 0x6822, bosco_interrupt_enable_3_w ),
	
		new Memory_WriteAddress( 0x8000, 0x83ff, videoram_w ),
		new Memory_WriteAddress( 0x8400, 0x87ff, bosco_videoram2_w ),
		new Memory_WriteAddress( 0x8800, 0x8bff, colorram_w ),
		new Memory_WriteAddress( 0x8c00, 0x8fff, bosco_colorram2_w ),
		new Memory_WriteAddress( 0x7800, 0x97ff, bosco_sharedram_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	
	static InputPortPtr input_ports_bosco = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* DSW0 */
		PORT_DIPNAME( 0x07, 0x07, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x01, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Free_Play") );
		/* TODO: bonus scores are different for 5 lives */
		PORT_DIPNAME( 0x38, 0x08, "Bonus Fighter" );
		PORT_DIPSETTING(    0x30, "15K 50K" );
		PORT_DIPSETTING(    0x38, "20K 70K" );
		PORT_DIPSETTING(    0x08, "10K 50K 50K" );
		PORT_DIPSETTING(    0x10, "15K 50K 50K" );
		PORT_DIPSETTING(    0x18, "15K 70K 70K" );
		PORT_DIPSETTING(    0x20, "20K 70K 70K" );
		PORT_DIPSETTING(    0x28, "30K 100K 100K" );
		PORT_DIPSETTING(    0x00, "None" );
		PORT_DIPNAME( 0xc0, 0x80, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "1" );
		PORT_DIPSETTING(    0x40, "2" );
		PORT_DIPSETTING(    0x80, "3" );
		PORT_DIPSETTING(    0xc0, "5" );
	
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x01, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x02, "Hardest" );
		PORT_DIPSETTING(    0x00, "Auto" );
		PORT_DIPNAME( 0x04, 0x04, "2 Credits Game" );
		PORT_DIPSETTING(    0x00, "1 Player" );
		PORT_DIPSETTING(    0x04, "2 Players" );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x10, "Freeze" );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, "Allow Continue" );
		PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x40, 0x40, "Test ????" );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
	
		PORT_START(); 	/* FAKE */
		/* The player inputs are not memory mapped, they are handled by an I/O chip. */
		/* These fake input ports are read by galaga_customio_data_r() */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1, 1 );
		PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );
		PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START(); 	/* FAKE */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL, 1 );
		PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );
		PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START(); 	/* FAKE */
		/* the button here is used to trigger the sound in the test screen */
		PORT_BITX(0x03, IP_ACTIVE_LOW, IPT_BUTTON1,	null, IP_KEY_DEFAULT, IP_JOY_DEFAULT );
		PORT_BIT_IMPULSE( 0x04, IP_ACTIVE_LOW, IPT_START1, 1 );
		PORT_BIT_IMPULSE( 0x08, IP_ACTIVE_LOW, IPT_START2, 1 );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_LOW, IPT_COIN1, 1 );
		PORT_BIT_IMPULSE( 0x20, IP_ACTIVE_LOW, IPT_COIN2, 1 );
		PORT_BIT_IMPULSE( 0x40, IP_ACTIVE_LOW, IPT_COIN3, 1 );
		PORT_SERVICE( 0x80, IP_ACTIVE_LOW );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_boscomd = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* DSW0 */
		PORT_DIPNAME( 0x07, 0x07, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x01, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Free_Play") );
		/* TODO: bonus scores are different for 5 lives */
		PORT_DIPNAME( 0x38, 0x08, "Bonus Fighter" );
		PORT_DIPSETTING(    0x30, "15K 50K" );
		PORT_DIPSETTING(    0x38, "20K 70K" );
		PORT_DIPSETTING(    0x08, "10K 50K 50K" );
		PORT_DIPSETTING(    0x10, "15K 50K 50K" );
		PORT_DIPSETTING(    0x18, "15K 70K 70K" );
		PORT_DIPSETTING(    0x20, "20K 70K 70K" );
		PORT_DIPSETTING(    0x28, "30K 100K 100K" );
		PORT_DIPSETTING(    0x00, "None" );
		PORT_DIPNAME( 0xc0, 0x80, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "1" );
		PORT_DIPSETTING(    0x40, "2" );
		PORT_DIPSETTING(    0x80, "3" );
		PORT_DIPSETTING(    0xc0, "5" );
	
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x01, 0x01, "2 Credits Game" );
		PORT_DIPSETTING(    0x00, "1 Player" );
		PORT_DIPSETTING(    0x01, "2 Players" );
		PORT_DIPNAME( 0x06, 0x06, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x06, "Medium" );
		PORT_DIPSETTING(    0x04, "Hardest" );
		PORT_DIPSETTING(    0x00, "Auto" );
		PORT_DIPNAME( 0x08, 0x08, "Allow Continue" );
		PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, "Freeze" );
		PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x40, "Test ????" );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
	
		PORT_START(); 	/* FAKE */
		/* The player inputs are not memory mapped, they are handled by an I/O chip. */
		/* These fake input ports are read by galaga_customio_data_r() */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1, 1 );
		PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );
		PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START(); 	/* FAKE */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_COCKTAIL);
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL, 1 );
		PORT_BITX(0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );
		PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START(); 	/* FAKE */
		/* the button here is used to trigger the sound in the test screen */
		PORT_BITX(0x03, IP_ACTIVE_LOW, IPT_BUTTON1,	null, IP_KEY_DEFAULT, IP_JOY_DEFAULT );
		PORT_BIT_IMPULSE( 0x04, IP_ACTIVE_LOW, IPT_START1, 1 );
		PORT_BIT_IMPULSE( 0x08, IP_ACTIVE_LOW, IPT_START2, 1 );
		PORT_BIT_IMPULSE( 0x10, IP_ACTIVE_LOW, IPT_COIN1, 1 );
		PORT_BIT_IMPULSE( 0x20, IP_ACTIVE_LOW, IPT_COIN2, 1 );
		PORT_BIT_IMPULSE( 0x40, IP_ACTIVE_LOW, IPT_COIN3, 1 );
		PORT_SERVICE( 0x80, IP_ACTIVE_LOW );
	INPUT_PORTS_END(); }}; 
	
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		256,	/* 256 characters */
		2,	/* 2 bits per pixel */
		new int[] { 0, 4 },      /* the two bitplanes for 4 pixels are packed into one byte */
		new int[] { 8*8+0, 8*8+1, 8*8+2, 8*8+3, 0, 1, 2, 3 },   /* bits are packed in groups of four */
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },   /* characters are rotated 90 degrees */
		16*8	       /* every char takes 16 bytes */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,		/* 16*16 sprites */
		64,		/* 128 sprites */
		2,		/* 2 bits per pixel */
		new int[] { 0, 4 },	/* the two bitplanes for 4 pixels are packed into one byte */
		new int[] { 8*8, 8*8+1, 8*8+2, 8*8+3, 16*8+0, 16*8+1, 16*8+2, 16*8+3,
				24*8+0, 24*8+1, 24*8+2, 24*8+3, 0, 1, 2, 3  },
		new int[] { 0 * 8, 1 * 8, 2 * 8, 3 * 8, 4 * 8, 5 * 8, 6 * 8, 7 * 8,
				32 * 8, 33 * 8, 34 * 8, 35 * 8, 36 * 8, 37 * 8, 38 * 8, 39 * 8 },
		64*8	/* every sprite takes 64 bytes */
	);
	
	static GfxLayout dotlayout = new GfxLayout
	(
		4,4,	/* 4*4 characters */
		8,	/* 8 characters */
		2,	/* 2 bits per pixel */
		new int[] { 6, 7 },
		new int[] { 3*8, 2*8, 1*8, 0*8 },
		new int[] { 3*32, 2*32, 1*32, 0*32 },
		16*8	/* every char takes 16 consecutive bytes */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,	        0, 64 ),
		new GfxDecodeInfo( REGION_GFX2, 0, spritelayout,	 64*4, 64 ),
		new GfxDecodeInfo( REGION_GFX3, 0, dotlayout,    64*4+64*4,	1 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	
	static namco_interface namco_interface = new namco_interface
	(
		3072000/32,	/* sample rate */
		3,			/* number of voices */
		50,			/* playback volume */
		REGION_SOUND1	/* memory region */
        );
	
	
	static String bosco_sample_names[] =
	{
		"*bosco",
		"midbang.wav",
		"bigbang.wav",
		"shot.wav",
		null	/* end of array */
	};
	
	static Samplesinterface samples_interface = new Samplesinterface
        (
		3,	/* 3 channels */
		80,	/* volume */
		bosco_sample_names
        );
	
	
	static CustomSound_interface custom_interface = new CustomSound_interface
	(
		bosco_sh_start,
		bosco_sh_stop,
		null
	);
	
	
	static MachineDriver machine_driver_bosco = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				3125000,	/* 3.125 MHz */
				readmem_cpu1,writemem_cpu1,null,null,
				bosco_interrupt_1,1
			),
			new MachineCPU(
				CPU_Z80,
				3125000,	/* 3.125 MHz */
				readmem_cpu2,writemem_cpu2,null,null,
				bosco_interrupt_2,1
			),
			new MachineCPU(
				CPU_Z80,
				3125000,	/* 3.125 MHz */
				readmem_cpu3,writemem_cpu3,null,null,
				bosco_interrupt_3,2
			)
		},
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		100,	/* 100 CPU slices per frame - an high value to ensure proper */
				/* synchronization of the CPUs */
		bosco_init_machine,
	
		/* video hardware */
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),
		gfxdecodeinfo,
		32+64,64*4+64*4+4,	/* 32 for the characters, 64 for the stars */
		bosco_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		bosco_vh_start,
		bosco_vh_stop,
		bosco_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_NAMCO,
				namco_interface
			),
			new MachineSound(
				SOUND_CUSTOM,
				custom_interface
			),
			new MachineSound(
				SOUND_SAMPLES,
				samples_interface
			)
		}
	);
	
	
	
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_bosco = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code for the first CPU  */
		ROM_LOAD( "bos3_1.bin",   0x0000, 0x1000, 0x96021267 );
		ROM_LOAD( "bos1_2.bin",   0x1000, 0x1000, 0x2d8f3ebe );
		ROM_LOAD( "bos1_3.bin",   0x2000, 0x1000, 0xc80ccfa5 );
		ROM_LOAD( "bos1_4b.bin",  0x3000, 0x1000, 0xa3f7f4ab );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "bos1_5c.bin",  0x0000, 0x1000, 0xa7c8e432 );
		ROM_LOAD( "bos3_6.bin",   0x1000, 0x1000, 0x4543cf82 );
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU  */
		ROM_LOAD( "2900.3e",      0x0000, 0x1000, 0xd45a4911 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "5300.5d",      0x0000, 0x1000, 0xa956d3c5 );
	
		ROM_REGION( 0x1000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "5200.5e",      0x0000, 0x1000, 0xe869219c );
	
		ROM_REGION( 0x0100, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "prom.2d",      0x0000, 0x0100, 0x9b69b543 );/* dots */
	
		ROM_REGION( 0x0260, REGION_PROMS, 0 );
		ROM_LOAD( "bosco.6b",     0x0000, 0x0020, 0xd2b96fb0 );/* palette */
		ROM_LOAD( "bosco.4m",     0x0020, 0x0100, 0x4e15d59c );/* lookup table */
		ROM_LOAD( "prom.1d",      0x0120, 0x0100, 0xde2316c6 );/* ?? */
		ROM_LOAD( "prom.2r",      0x0220, 0x0020, 0xb88d5ba9 );/* ?? */
		ROM_LOAD( "prom.7h",      0x0240, 0x0020, 0x87d61353 );/* ?? */
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "bosco.spr",    0x0000, 0x0100, 0xee8ca3a8 );
		ROM_LOAD( "prom.5c",      0x0100, 0x0100, 0x77245b66 );/* timing - not used */
	
		ROM_REGION( 0x3000, REGION_SOUND2, 0 );/* ROMs for digitised speech */
		ROM_LOAD( "4900.5n",      0x0000, 0x1000, 0x09acc978 );
		ROM_LOAD( "5000.5m",      0x1000, 0x1000, 0xe571e959 );
		ROM_LOAD( "5100.5l",      0x2000, 0x1000, 0x17ac9511 );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_boscoo = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code for the first CPU  */
		ROM_LOAD( "bos1_1.bin",   0x0000, 0x1000, 0x0d9920e7 );
		ROM_LOAD( "bos1_2.bin",   0x1000, 0x1000, 0x2d8f3ebe );
		ROM_LOAD( "bos1_3.bin",   0x2000, 0x1000, 0xc80ccfa5 );
		ROM_LOAD( "bos1_4b.bin",  0x3000, 0x1000, 0xa3f7f4ab );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "bos1_5c.bin",  0x0000, 0x1000, 0xa7c8e432 );
		ROM_LOAD( "2800.3h",      0x1000, 0x1000, 0x31b8c648 );
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU  */
		ROM_LOAD( "2900.3e",      0x0000, 0x1000, 0xd45a4911 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "5300.5d",      0x0000, 0x1000, 0xa956d3c5 );
	
		ROM_REGION( 0x1000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "5200.5e",      0x0000, 0x1000, 0xe869219c );
	
		ROM_REGION( 0x0100, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "prom.2d",      0x0000, 0x0100, 0x9b69b543 );/* dots */
	
		ROM_REGION( 0x0260, REGION_PROMS, 0 );
		ROM_LOAD( "bosco.6b",     0x0000, 0x0020, 0xd2b96fb0 );/* palette */
		ROM_LOAD( "bosco.4m",     0x0020, 0x0100, 0x4e15d59c );/* lookup table */
		ROM_LOAD( "prom.1d",      0x0120, 0x0100, 0xde2316c6 );/* ?? */
		ROM_LOAD( "prom.2r",      0x0220, 0x0020, 0xb88d5ba9 );/* ?? */
		ROM_LOAD( "prom.7h",      0x0240, 0x0020, 0x87d61353 );/* ?? */
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "bosco.spr",    0x0000, 0x0100, 0xee8ca3a8 );
		ROM_LOAD( "prom.5c",      0x0100, 0x0100, 0x77245b66 );/* timing - not used */
	
		ROM_REGION( 0x3000, REGION_SOUND2, 0 );/* ROMs for digitised speech */
		ROM_LOAD( "4900.5n",      0x0000, 0x1000, 0x09acc978 );
		ROM_LOAD( "5000.5m",      0x1000, 0x1000, 0xe571e959 );
		ROM_LOAD( "5100.5l",      0x2000, 0x1000, 0x17ac9511 );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_boscoo2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code for the first CPU  */
		ROM_LOAD( "bos1_1.bin",   0x0000, 0x1000, 0x0d9920e7 );
		ROM_LOAD( "bos1_2.bin",   0x1000, 0x1000, 0x2d8f3ebe );
		ROM_LOAD( "bos1_3.bin",   0x2000, 0x1000, 0xc80ccfa5 );
		ROM_LOAD( "bos1_4.3k",    0x3000, 0x1000, 0x7ebea2b8 );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "bos1_5b.3j",   0x0000, 0x1000, 0x3d6955a8 );
		ROM_LOAD( "2800.3h",      0x1000, 0x1000, 0x31b8c648 );
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU  */
		ROM_LOAD( "2900.3e",      0x0000, 0x1000, 0xd45a4911 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "5300.5d",      0x0000, 0x1000, 0xa956d3c5 );
	
		ROM_REGION( 0x1000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "5200.5e",      0x0000, 0x1000, 0xe869219c );
	
		ROM_REGION( 0x0100, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "prom.2d",      0x0000, 0x0100, 0x9b69b543 );/* dots */
	
		ROM_REGION( 0x0260, REGION_PROMS, 0 );
		ROM_LOAD( "bosco.6b",     0x0000, 0x0020, 0xd2b96fb0 );/* palette */
		ROM_LOAD( "bosco.4m",     0x0020, 0x0100, 0x4e15d59c );/* lookup table */
		ROM_LOAD( "prom.1d",      0x0120, 0x0100, 0xde2316c6 );/* ?? */
		ROM_LOAD( "prom.2r",      0x0220, 0x0020, 0xb88d5ba9 );/* ?? */
		ROM_LOAD( "prom.7h",      0x0240, 0x0020, 0x87d61353 );/* ?? */
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "bosco.spr",    0x0000, 0x0100, 0xee8ca3a8 );
		ROM_LOAD( "prom.5c",      0x0100, 0x0100, 0x77245b66 );/* timing - not used */
	
		ROM_REGION( 0x3000, REGION_SOUND2, 0 );/* ROMs for digitised speech */
		ROM_LOAD( "4900.5n",      0x0000, 0x1000, 0x09acc978 );
		ROM_LOAD( "5000.5m",      0x1000, 0x1000, 0xe571e959 );
		ROM_LOAD( "5100.5l",      0x2000, 0x1000, 0x17ac9511 );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_boscomd = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code for the first CPU  */
		ROM_LOAD( "3n",       0x0000, 0x1000, 0x441b501a );
		ROM_LOAD( "3m",       0x1000, 0x1000, 0xa3c5c7ef );
		ROM_LOAD( "3l",       0x2000, 0x1000, 0x6ca9a0cf );
		ROM_LOAD( "3k",       0x3000, 0x1000, 0xd83bacc5 );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "3j",       0x0000, 0x1000, 0x4374e39a );
		ROM_LOAD( "3h",       0x1000, 0x1000, 0x04e9fcef );
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU  */
		ROM_LOAD( "2900.3e",      0x0000, 0x1000, 0xd45a4911 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "5300.5d",      0x0000, 0x1000, 0xa956d3c5 );
	
		ROM_REGION( 0x1000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "5200.5e",      0x0000, 0x1000, 0xe869219c );
	
		ROM_REGION( 0x0100, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "prom.2d",      0x0000, 0x0100, 0x9b69b543 );/* dots */
	
		ROM_REGION( 0x0260, REGION_PROMS, 0 );
		ROM_LOAD( "bosco.6b",     0x0000, 0x0020, 0xd2b96fb0 );/* palette */
		ROM_LOAD( "bosco.4m",     0x0020, 0x0100, 0x4e15d59c );/* lookup table */
		ROM_LOAD( "prom.1d",      0x0120, 0x0100, 0xde2316c6 );/* ?? */
		ROM_LOAD( "prom.2r",      0x0220, 0x0020, 0xb88d5ba9 );/* ?? */
		ROM_LOAD( "prom.7h",      0x0240, 0x0020, 0x87d61353 );/* ?? */
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "bosco.spr",    0x0000, 0x0100, 0xee8ca3a8 );
		ROM_LOAD( "prom.5c",      0x0100, 0x0100, 0x77245b66 );/* timing - not used */
	
		ROM_REGION( 0x3000, REGION_SOUND2, 0 );/* ROMs for digitised speech */
		ROM_LOAD( "4900.5n",      0x0000, 0x1000, 0x09acc978 );
		ROM_LOAD( "5000.5m",      0x1000, 0x1000, 0xe571e959 );
		ROM_LOAD( "5100.5l",      0x2000, 0x1000, 0x17ac9511 );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_boscomdo = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code for the first CPU  */
		ROM_LOAD( "2300.3n",      0x0000, 0x1000, 0xdb6128b0 );
		ROM_LOAD( "2400.3m",      0x1000, 0x1000, 0x86907614 );
		ROM_LOAD( "2500.3l",      0x2000, 0x1000, 0xa21fae11 );
		ROM_LOAD( "2600.3k",      0x3000, 0x1000, 0x11d6ae23 );
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "2700.3j",      0x0000, 0x1000, 0x7254e65e );
		ROM_LOAD( "2800.3h",      0x1000, 0x1000, 0x31b8c648 );
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU  */
		ROM_LOAD( "2900.3e",      0x0000, 0x1000, 0xd45a4911 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "5300.5d",      0x0000, 0x1000, 0xa956d3c5 );
	
		ROM_REGION( 0x1000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "5200.5e",      0x0000, 0x1000, 0xe869219c );
	
		ROM_REGION( 0x0100, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "prom.2d",      0x0000, 0x0100, 0x9b69b543 );/* dots */
	
		ROM_REGION( 0x0260, REGION_PROMS, 0 );
		ROM_LOAD( "bosco.6b",     0x0000, 0x0020, 0xd2b96fb0 );/* palette */
		ROM_LOAD( "bosco.4m",     0x0020, 0x0100, 0x4e15d59c );/* lookup table */
		ROM_LOAD( "prom.1d",      0x0120, 0x0100, 0xde2316c6 );/* ?? */
		ROM_LOAD( "prom.2r",      0x0220, 0x0020, 0xb88d5ba9 );/* ?? */
		ROM_LOAD( "prom.7h",      0x0240, 0x0020, 0x87d61353 );/* ?? */
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound prom */
		ROM_LOAD( "bosco.spr",    0x0000, 0x0100, 0xee8ca3a8 );
		ROM_LOAD( "prom.5c",      0x0100, 0x0100, 0x77245b66 );/* timing - not used */
	
		ROM_REGION( 0x3000, REGION_SOUND2, 0 );/* ROMs for digitised speech */
		ROM_LOAD( "4900.5n",      0x0000, 0x1000, 0x09acc978 );
		ROM_LOAD( "5000.5m",      0x1000, 0x1000, 0xe571e959 );
		ROM_LOAD( "5100.5l",      0x2000, 0x1000, 0x17ac9511 );
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_bosco	   = new GameDriver("1981"	,"bosco"	,"bosco.java"	,rom_bosco,null	,machine_driver_bosco	,input_ports_bosco	,null	,ROT0	,	"Namco", "Bosconian (new version)" );
	public static GameDriver driver_boscoo	   = new GameDriver("1981"	,"boscoo"	,"bosco.java"	,rom_boscoo,driver_bosco	,machine_driver_bosco	,input_ports_bosco	,null	,ROT0	,	"Namco", "Bosconian (old version)" );
	public static GameDriver driver_boscoo2	   = new GameDriver("1981"	,"boscoo2"	,"bosco.java"	,rom_boscoo2,driver_bosco	,machine_driver_bosco	,input_ports_bosco	,null	,ROT0	,	"Namco", "Bosconian (older version)" );
	public static GameDriver driver_boscomd	   = new GameDriver("1981"	,"boscomd"	,"bosco.java"	,rom_boscomd,driver_bosco	,machine_driver_bosco	,input_ports_boscomd	,null	,ROT0	,	"[Namco] (Midway license)", "Bosconian (Midway, new version)" );
	public static GameDriver driver_boscomdo	   = new GameDriver("1981"	,"boscomdo"	,"bosco.java"	,rom_boscomdo,driver_bosco	,machine_driver_bosco	,input_ports_boscomd	,null	,ROT0	,	"[Namco] (Midway license)", "Bosconian (Midway, old version)" );
}
