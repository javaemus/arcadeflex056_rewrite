/***************************************************************************

The New Zealand Story driver, used for tnzs & tnzs2.

TODO: - Find out how the hardware credit-counter works (MPU)
	  - Verify dip switches
	  - Fix video offsets (See Dr Toppel in Flip-Screen - also
	       affects Chuka Taisen)
	  - Video scroll side flicker in Chuka Taisen, Insector X and Dr Toppel

	Arkanoid 2:
	  - What do writes at $f400 do ?
	  - Why does the game zero the $fd00 area ?
	Extrmatn:
	  - What do reads from $f600 do ? (discarded)
	Chuka Taisen:
	  - What do writes at  $f400 do ? (value 40h)
	  - What do reads from $f600 do in service mode ?
	Dr Toppel:
	  - What do writes at  $f400 do ? (value 40h)
	  - What do reads from $f600 do in service mode ?

****************************************************************************

extrmatn and arknoid2 have a special test mode. The correct procedure to make
it succeed is as follows:
- enter service mode
- on the color test screen, press 2 (player 2 start)
- set dip switch 1 and dip switch 2 so that they read 00000001
- reset the emulation, and skip the previous step.
- press 5 (coin 1). Text at the bottom will change to "CHECKING NOW".
- use all the inputs, including tilt, until all inputs are OK
- press 5 (coin 1) - to confirm that coin lockout 1 works
- press 5 (coin 1) - to confirm that coin lockout 2 works
- set dip switch 1 to 00000000
- set dip switch 1 to 10101010
- set dip switch 1 to 11111111
- set dip switch 2 to 00000000
- set dip switch 2 to 10101010
- set dip switch 2 to 11111111
- speaker should now output a tone
- press 5 (coin 1) , to confirm that OPN works
- press 5 (coin 1) , to confirm that SSGCH1 works
- press 5 (coin 1) , to confirm that SSGCH2 works
- press 5 (coin 1) , to confirm that SSGCH3 works
- finished ("CHECK ALL OK!")

****************************************************************************

The New Zealand Story memory map (preliminary)

CPU #1
0000-7fff ROM
8000-bfff banked - banks 0-1 RAM; banks 2-7 ROM
c000-dfff object RAM, including:
  c000-c1ff sprites (code, low byte)
  c200-c3ff sprites (x-coord, low byte)
  c400-c5ff tiles (code, low byte)

  d000-d1ff sprites (code, high byte)
  d200-d3ff sprites (x-coord and colour, high byte)
  d400-d5ff tiles (code, high byte)
  d600-d7ff tiles (colour)
e000-efff RAM shared with CPU #2
f000-ffff VDC RAM, including:
  f000-f1ff sprites (y-coord)
  f200-f2ff scrolling info
  f300-f301 vdc controller
  f302-f303 scroll x-coords (high bits)
  f600      bankswitch
  f800-fbff palette

CPU #2
0000-7fff ROM
8000-9fff banked ROM
a000      bankswitch
b000-b001 YM2203 interface (with DIPs on YM2203 ports)
c000-c001 I8742 MCU
e000-efff RAM shared with CPU #1
f000-f003 inputs (used only by Arkanoid 2)

****************************************************************************/
/***************************************************************************

				Arkanoid 2 - Revenge of Doh!
					(C) 1987 Taito

						driver by

				Luca Elia (l.elia@tin.it)
				Mirko Buffoni

- The game doesn't write to f800-fbff (static palette)



			Interesting routines (main cpu)
			-------------------------------

1ed	prints the test screen (first string at 206)

47a	prints dipsw1&2 e 1p&2p paddleL values:
	e821		IN DIPSW1		e823-4	1P PaddleL (lo-hi)
	e822		IN DIPSW2		e825-6	2P PaddleL (lo-hi)

584	prints OK or NG on each entry:
	if (*addr)!=0 { if (*addr)!=2 OK else NG }
	e880	1P PADDLEL		e88a	IN SERVICE
	e881	1P PADDLER		e88b	IN TILT
	e882	1P BUTTON		e88c	OUT LOCKOUT1
	e883	1P START		e88d	OUT LOCKOUT2
	e884	2P PADDLEL		e88e	IN DIP-SW1
	e885	2P PADDLER		e88f	IN DIP-SW2
	e886	2P BUTTON		e890	SND OPN
	e887	2P START		e891	SND SSGCH1
	e888	IN COIN1		e892	SND SSGCH2
	e889	IN COIN2		e893	SND SSGCH3

672	prints a char
715	prints a string (0 terminated)

		Shared Memory (values written mainly by the sound cpu)
		------------------------------------------------------

e001=dip-sw A 	e399=coin counter value		e72c-d=1P paddle (lo-hi)
e002=dip-sw B 	e3a0-2=1P score/10 (BCD)	e72e-f=2P paddle (lo-hi)
e008=level=2*(shown_level-1)+x <- remember it's a binary tree (42 last)
e7f0=country code(from 9fde in sound rom)
e807=counter, reset by sound cpu, increased by main cpu each vblank
e80b=test progress=0(start) 1(first 8) 2(all ok) 3(error)
ec09-a~=ed05-6=xy pos of cursor in hi-scores
ec81-eca8=hi-scores(8bytes*5entries)

addr	bit	name		active	addr	bit	name		active
e72d	6	coin[1]		low		e729	1	2p select	low
		5	service		high			0	1p select	low
		4	coin[2]		low

addr	bit	name		active	addr	bit	name		active
e730	7	tilt		low		e7e7	4	1p fire		low
										0	2p fire		low

			Interesting routines (sound cpu)
			--------------------------------

4ae	check starts	B73,B7a,B81,B99	coin related
8c1	check coins		62e lockout check		664	dsw check

			Interesting locations (sound cpu)
			---------------------------------

d006=each bit is on if a corresponding location (e880-e887) has changed
d00b=(c001)>>4=tilt if 0E (security sequence must be reset?)
addr	bit	name		active
d00c	7	tilt
		6	?service?
		5	coin2		low
		4	coin1		low

d00d=each bit is on if the corresponding location (e880-e887) is 1 (OK)
d00e=each of the 4 MSBs is on if ..
d00f=FF if tilt, 00 otherwise
d011=if 00 checks counter, if FF doesn't
d23f=input port 1 value

***************************************************************************/
/***************************************************************************

Kageki
(c) 1988 Taito Corporation

Driver by Takahiro Nogi (nogi@kt.rim.or.jp) 1999/11/06

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
import static WIP2.mame056.mame.Machine;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.machine.tnzs.*;
import static WIP2.mame056.vidhrdw.tnzs.*;

import static WIP2.mame056.sound._2203intf.*;
import static WIP2.mame056.sound._2203intfH.*;

public class tnzs
{
	/* prototypes for functions in ../machine/tnzs.c */
	public static UBytePtr tnzs_objram=new UBytePtr(), tnzs_workram=new UBytePtr();
	public static UBytePtr tnzs_vdcram=new UBytePtr(), tnzs_scrollram=new UBytePtr();
	
	/* max samples */
	public static int	MAX_SAMPLES = 0x2f;
	
	public static ShStartPtr kageki_init_samples = new ShStartPtr() {
            public int handler(MachineSound msound) {
                GameSamples samples;
		UBytePtr scan, dest;
                UBytePtr src;
		int start, size;
		int i, n;
	
		size = MAX_SAMPLES;
	
		if ((Machine.samples = new GameSamples(size)) == null) return 1;
	
		samples = Machine.samples;
		samples.total = MAX_SAMPLES;
	
		for (i = 0; i < samples.total; i++)
		{
			src = new UBytePtr(memory_region(REGION_SOUND1), 0x0090);
			start = (src.read((i * 2) + 1) * 256) + src.read((i * 2));
			scan = new UBytePtr(src, start);
			size = 0;
	
			// check sample length
			while (true)
			{
				if (scan.read() == 0x00)
				{
					break;
				} else {
					size++;
				}
                                scan.inc();
			}
			if ((samples.sample[i] = new GameSample( size )) == null) return 1;
	
			if (start < 0x100) start = size = 0;
	
			samples.sample[i].smpfreq = 7000;	/* 7 KHz??? */
			samples.sample[i].resolution = 8;	/* 8 bit */
			samples.sample[i].length = size;
	
			// signed 8-bit sample to unsigned 8-bit sample convert
			/*TODO*///dest = new UBytePtr(samples.sample[i].data);
			/*TODO*///scan = new UBytePtr(src, start);
			/*TODO*///for (n = 0; n < size; n++)
			/*TODO*///{
			/*TODO*///	*dest++ = ((*scan++) ^ 0x80);
			/*TODO*///}
		//	logerror("samples num:%02X ofs:%04X lng:%04X\n", i, start, size);
		}
	
		return 0;
            }
        };
	
	static int kageki_csport_sel = 0;
	public static ReadHandlerPtr kageki_csport_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int	dsw, dsw1, dsw2;
	
		dsw1 = readinputport(0); 		// DSW1
		dsw2 = readinputport(1); 		// DSW2
	
		switch (kageki_csport_sel)
		{
			case	0x00:			// DSW2 5,1 / DSW1 5,1
				dsw = (((dsw2 & 0x10) >> 1) | ((dsw2 & 0x01) << 2) | ((dsw1 & 0x10) >> 3) | ((dsw1 & 0x01) >> 0));
				break;
			case	0x01:			// DSW2 7,3 / DSW1 7,3
				dsw = (((dsw2 & 0x40) >> 3) | ((dsw2 & 0x04) >> 0) | ((dsw1 & 0x40) >> 5) | ((dsw1 & 0x04) >> 2));
				break;
			case	0x02:			// DSW2 6,2 / DSW1 6,2
				dsw = (((dsw2 & 0x20) >> 2) | ((dsw2 & 0x02) << 1) | ((dsw1 & 0x20) >> 4) | ((dsw1 & 0x02) >> 1));
				break;
			case	0x03:			// DSW2 8,4 / DSW1 8,4
				dsw = (((dsw2 & 0x80) >> 4) | ((dsw2 & 0x08) >> 1) | ((dsw1 & 0x80) >> 6) | ((dsw1 & 0x08) >> 3));
				break;
			default:
				dsw = 0x00;
			//	logerror("kageki_csport_sel error !! (0x%08X)\n", kageki_csport_sel);
		}
	
		return (dsw & 0xff);
	} };
	
	public static WriteHandlerPtr kageki_csport_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		String mess;
	
		if (data > 0x3f)
		{
			// read dipsw port
			kageki_csport_sel = (data & 0x03);
		} else {
			if (data > MAX_SAMPLES)
			{
				// stop samples
				sample_stop(0);
				/*TODO*///sprintf(mess, "VOICE:%02X STOP", data);
			} else {
				// play samples
				sample_start(0, data, 0);
				/*TODO*///sprintf(mess, "VOICE:%02X PLAY", data);
			}
		//	usrintf_showmessage(mess);
		}
	} };
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_BANK1 ), /* ROM + RAM */
		new Memory_ReadAddress( 0xc000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress( 0xe000, 0xefff, tnzs_workram_r ),	/* WORK RAM (shared by the 2 z80's */
		new Memory_ReadAddress( 0xf000, 0xf1ff, MRA_RAM ),	/* VDC RAM */
		new Memory_ReadAddress( 0xf600, 0xf600, MRA_NOP ),	/* ? */
		new Memory_ReadAddress( 0xf800, 0xfbff, MRA_RAM ),	/* not in extrmatn and arknoid2 (PROMs instead) */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_BANK1 ),	/* ROM + RAM */
		new Memory_WriteAddress( 0xc000, 0xdfff, MWA_RAM, tnzs_objram ),
		new Memory_WriteAddress( 0xe000, 0xefff, tnzs_workram_w, tnzs_workram ),
		new Memory_WriteAddress( 0xf000, 0xf1ff, MWA_RAM, tnzs_vdcram ),
		new Memory_WriteAddress( 0xf200, 0xf3ff, MWA_RAM, tnzs_scrollram ), /* scrolling info */
		new Memory_WriteAddress( 0xf400, 0xf400, MWA_NOP ),	/* ? */
		new Memory_WriteAddress( 0xf600, 0xf600, tnzs_bankswitch_w ),
		/* arknoid2, extrmatn, plumppop and drtoppel have PROMs instead of RAM */
		/* drtoppel writes here anyway! (maybe leftover from tests during development) */
		/* so the handler is patched out in init_drtopple() */
		new Memory_WriteAddress( 0xf800, 0xfbff, paletteram_xRRRRRGGGGGBBBBB_w, paletteram ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress sub_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x9fff, MRA_BANK2 ),
		new Memory_ReadAddress( 0xb000, 0xb000, YM2203_status_port_0_r ),
		new Memory_ReadAddress( 0xb001, 0xb001, YM2203_read_port_0_r ),
		new Memory_ReadAddress( 0xc000, 0xc001, tnzs_mcu_r ),	/* plain input ports in insectx (memory handler */
										/* changed in insectx_init() ) */
		new Memory_ReadAddress( 0xd000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress( 0xe000, 0xefff, tnzs_workram_sub_r ),
		new Memory_ReadAddress( 0xf000, 0xf003, arknoid2_sh_f000_r ),	/* paddles in arkanoid2/plumppop. The ports are */
							/* read but not used by the other games, and are not read at */
							/* all by insectx. */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress sub_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x9fff, MWA_ROM ),
		new Memory_WriteAddress( 0xa000, 0xa000, tnzs_bankswitch1_w ),
		new Memory_WriteAddress( 0xb000, 0xb000, YM2203_control_port_0_w ),
		new Memory_WriteAddress( 0xb001, 0xb001, YM2203_write_port_0_w ),
		new Memory_WriteAddress( 0xc000, 0xc001, tnzs_mcu_w ),	/* not present in insectx */
		new Memory_WriteAddress( 0xd000, 0xdfff, MWA_RAM ),
		new Memory_WriteAddress( 0xe000, 0xefff, tnzs_workram_sub_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress kageki_sub_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x9fff, MRA_BANK2 ),
		new Memory_ReadAddress( 0xb000, 0xb000, YM2203_status_port_0_r ),
		new Memory_ReadAddress( 0xb001, 0xb001, YM2203_read_port_0_r ),
		new Memory_ReadAddress( 0xc000, 0xc000, input_port_2_r ),
		new Memory_ReadAddress( 0xc001, 0xc001, input_port_3_r ),
		new Memory_ReadAddress( 0xc002, 0xc002, input_port_4_r ),
		new Memory_ReadAddress( 0xd000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress( 0xe000, 0xefff, tnzs_workram_sub_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress kageki_sub_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x9fff, MWA_ROM ),
		new Memory_WriteAddress( 0xa000, 0xa000, tnzs_bankswitch1_w ),
		new Memory_WriteAddress( 0xb000, 0xb000, YM2203_control_port_0_w ),
		new Memory_WriteAddress( 0xb001, 0xb001, YM2203_write_port_0_w ),
		new Memory_WriteAddress( 0xd000, 0xdfff, MWA_RAM ),
		new Memory_WriteAddress( 0xe000, 0xefff, tnzs_workram_sub_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	/* the bootleg board is different, it has a third CPU (and of course no mcu) */
	
	public static WriteHandlerPtr tnzsb_sound_command_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		soundlatch_w.handler(offset,data);
		cpu_cause_interrupt(2,0xff);
	} };
	
	public static Memory_ReadAddress tnzsb_readmem1[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x9fff, MRA_BANK2 ),
		new Memory_ReadAddress( 0xb002, 0xb002, input_port_0_r ),
		new Memory_ReadAddress( 0xb003, 0xb003, input_port_1_r ),
		new Memory_ReadAddress( 0xc000, 0xc000, input_port_2_r ),
		new Memory_ReadAddress( 0xc001, 0xc001, input_port_3_r ),
		new Memory_ReadAddress( 0xc002, 0xc002, input_port_4_r ),
		new Memory_ReadAddress( 0xd000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress( 0xe000, 0xefff, tnzs_workram_sub_r ),
		new Memory_ReadAddress( 0xf000, 0xf003, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress tnzsb_writemem1[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x9fff, MWA_ROM ),
		new Memory_WriteAddress( 0xa000, 0xa000, tnzs_bankswitch1_w ),
		new Memory_WriteAddress( 0xb004, 0xb004, tnzsb_sound_command_w ),
		new Memory_WriteAddress( 0xd000, 0xdfff, MWA_RAM ),
		new Memory_WriteAddress( 0xe000, 0xefff, tnzs_workram_sub_w ),
		new Memory_WriteAddress( 0xf000, 0xf3ff, paletteram_xRRRRRGGGGGBBBBB_w, paletteram ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_ReadAddress tnzsb_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0xc000, 0xdfff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress tnzsb_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0xc000, 0xdfff, MWA_RAM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static IO_ReadPort tnzsb_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_ReadPort( 0x00, 0x00, YM2203_status_port_0_r  ),
		new IO_ReadPort( 0x02, 0x02, soundlatch_r  ),
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort tnzsb_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( 0x00, 0x00, YM2203_control_port_0_w  ),
		new IO_WritePort( 0x01, 0x01, YM2203_write_port_0_w  ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	
	
	static InputPortPtr input_ports_extrmatn = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
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
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_arknoid2 = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW1 - IN2 */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_6C") );
	
		PORT_START(); 		/* DSW2 - IN3 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Normal" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Very Hard" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "50k 150k" );
		PORT_DIPSETTING(    0x0c, "100k 200k" );
		PORT_DIPSETTING(    0x04, "50k Only" );
		PORT_DIPSETTING(    0x08, "100k Only" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x20, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x10, "4" );
		PORT_DIPSETTING(    0x00, "5" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, "Allow Continue" );
		PORT_DIPSETTING(    0x80, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
	
		PORT_START(); 		/* IN1 - read at c000 (sound cpu) */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* empty */
	
		PORT_START(); 		/* empty */
	
		PORT_START(); 		/* spinner 1 - read at f000/1 */
		PORT_ANALOG( 0x0fff, 0x0000, IPT_DIAL, 70, 15, 0, 0 );
		PORT_BIT   ( 0x1000, IP_ACTIVE_LOW,  IPT_COIN2 );
		PORT_BIT   ( 0x2000, IP_ACTIVE_HIGH, IPT_SERVICE1 );
		PORT_BIT   ( 0x4000, IP_ACTIVE_LOW,  IPT_COIN1 );
		PORT_BIT   ( 0x8000, IP_ACTIVE_LOW,  IPT_TILT );/* arbitrarily assigned, handled by the mcu */
	
		PORT_START(); 		/* spinner 2 - read at f002/3 */
		PORT_ANALOG( 0x0fff, 0x0000, IPT_DIAL | IPF_PLAYER2, 70, 15, 0, 0 );
		PORT_BIT   ( 0xf000, IP_ACTIVE_LOW,  IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_arknid2u = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW1 - IN2 */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
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
	
		PORT_START(); 		/* DSW2 - IN3 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Normal" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Very Hard" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "50k 150k" );
		PORT_DIPSETTING(    0x0c, "100k 200k" );
		PORT_DIPSETTING(    0x04, "50k Only" );
		PORT_DIPSETTING(    0x08, "100k Only" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x20, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x10, "4" );
		PORT_DIPSETTING(    0x00, "5" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, "Allow Continue" );
		PORT_DIPSETTING(    0x80, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
	
		PORT_START(); 		/* IN1 - read at c000 (sound cpu) */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* empty */
	
		PORT_START(); 		/* empty */
	
		PORT_START(); 		/* spinner 1 - read at f000/1 */
		PORT_ANALOG( 0x0fff, 0x0000, IPT_DIAL, 70, 15, 0, 0 );
		PORT_BIT   ( 0x1000, IP_ACTIVE_LOW,  IPT_COIN2 );
		PORT_BIT   ( 0x2000, IP_ACTIVE_HIGH, IPT_SERVICE1 );
		PORT_BIT   ( 0x4000, IP_ACTIVE_LOW,  IPT_COIN1 );
		PORT_BIT   ( 0x8000, IP_ACTIVE_LOW,  IPT_TILT );/* arbitrarily assigned, handled by the mcu */
	
		PORT_START(); 		/* spinner 2 - read at f002/3 */
		PORT_ANALOG( 0x0fff, 0x0000, IPT_DIAL | IPF_PLAYER2, 70, 15, 0, 0 );
		PORT_BIT   ( 0xf000, IP_ACTIVE_LOW,  IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_plumppop = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
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
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "50k 150k" );
		PORT_DIPSETTING(    0x0c, "70k 200k" );
		PORT_DIPSETTING(    0x04, "100k 250k" );
		PORT_DIPSETTING(    0x00, "200k 300k" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x20, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x10, "4" );
		PORT_DIPSETTING(    0x00, "5" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, "Allow Continue" );
		PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Yes") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_CHEAT );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_CHEAT | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START(); 		/* spinner 1 - read at f000/1 */
		PORT_ANALOG( 0xffff, 0x0000, IPT_DIAL, 70, 15, 0, 0 );
	
		PORT_START(); 		/* spinner 2 - read at f002/3 */
		PORT_ANALOG( 0xffff, 0x0000, IPT_DIAL | IPF_PLAYER2, 70, 15, 0, 0 );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_drtoppel = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
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
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x0c, "30000" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Unknown") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x20, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x10, "4" );
		PORT_DIPSETTING(    0x00, "5" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_chukatai = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
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
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "100k 300k 440k" );
		PORT_DIPSETTING(    0x00, "100k 300k 500k" );
		PORT_DIPSETTING(    0x0c, "100k 400k" );
		PORT_DIPSETTING(    0x04, "100k 500k" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "1" );
		PORT_DIPSETTING(    0x10, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x20, "4" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_tnzs = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
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
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "50000 150000" );
		PORT_DIPSETTING(    0x0c, "70000 200000" );
		PORT_DIPSETTING(    0x04, "100000 250000" );
		PORT_DIPSETTING(    0x08, "200000 300000" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x20, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x00, "4" );
		PORT_DIPSETTING(    0x10, "5" );
		PORT_DIPNAME( 0x40, 0x40, "Allow Continue" );
		PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_tnzsb = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_6C") );
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "50000 150000" );
		PORT_DIPSETTING(    0x0c, "70000 200000" );
		PORT_DIPSETTING(    0x04, "100000 250000" );
		PORT_DIPSETTING(    0x08, "200000 300000" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x20, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x00, "4" );
		PORT_DIPSETTING(    0x10, "5" );
		PORT_DIPNAME( 0x40, 0x40, "Allow Continue" );
		PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_tnzs2 = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_6C") );
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "10000 100000" );
		PORT_DIPSETTING(    0x0c, "10000 150000" );
		PORT_DIPSETTING(    0x08, "10000 200000" );
		PORT_DIPSETTING(    0x04, "10000 300000" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x20, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x00, "4" );
		PORT_DIPSETTING(    0x10, "5" );
		PORT_DIPNAME( 0x40, 0x40, "Allow Continue" );
		PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_insectx = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_6C") );
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "100k 200k 300k 440k" );
		PORT_DIPSETTING(    0x0c, "100k 400k" );
		PORT_DIPSETTING(    0x04, "100k 500k" );
		PORT_DIPSETTING(    0x00, "150000 Only" );
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "1" );
		PORT_DIPSETTING(    0x10, "2" );
		PORT_DIPSETTING(    0x30, "3" );
		PORT_DIPSETTING(    0x20, "4" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_kageki = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* DSW A */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04, IP_ACTIVE_LOW );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
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
	
		PORT_START(); 		/* DSW B */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, "Allow Continue" );
		PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Yes") );
	
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
	
		PORT_START(); 		/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START(); 		/* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_TILT );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	INPUT_PORTS_END(); }}; 
	
	
	static GfxLayout arknoid2_charlayout = new GfxLayout
	(
		16,16,
		4096,
		4,
		new int[] { 3*4096*32*8, 2*4096*32*8, 1*4096*32*8, 0*4096*32*8 },
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7,
				8*8+0,8*8+1,8*8+2,8*8+3,8*8+4,8*8+5,8*8+6,8*8+7},
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				16*8, 17*8, 18*8, 19*8, 20*8, 21*8, 22*8, 23*8 },
		32*8
	);
	
	static GfxLayout tnzs_charlayout = new GfxLayout
	(
		16,16,
		8192,
		4,
		new int[] { 3*8192*32*8, 2*8192*32*8, 1*8192*32*8, 0*8192*32*8 },
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7,
				8*8+0, 8*8+1, 8*8+2, 8*8+3, 8*8+4, 8*8+5, 8*8+6, 8*8+7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				16*8, 17*8, 18*8, 19*8, 20*8, 21*8, 22*8, 23*8 },
		32*8
	);
	
	static GfxLayout insectx_charlayout = new GfxLayout
	(
		16,16,
		8192,
		4,
		new int[] { 8, 0, 8192*64*8+8, 8192*64*8 },
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7,
				8*16+0, 8*16+1, 8*16+2, 8*16+3, 8*16+4, 8*16+5, 8*16+6, 8*16+7 },
		new int[] { 0*16, 1*16, 2*16, 3*16, 4*16, 5*16, 6*16, 7*16,
			16*16, 17*16, 18*16, 19*16, 20*16, 21*16, 22*16, 23*16 },
		64*8
	);
	
	static GfxDecodeInfo arknoid2_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, arknoid2_charlayout, 0, 32 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	static GfxDecodeInfo tnzs_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, tnzs_charlayout, 0, 32 ),
		new GfxDecodeInfo( -1 )	/* end of array */
	};
	
	static GfxDecodeInfo insectx_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, insectx_charlayout, 0, 32 ),
		new GfxDecodeInfo( -1 )	/* end of array */
	};
	
	
	
	public static YM2203interface ym2203_interface = new YM2203interface
	(
		1,			/* 1 chip */
		3000000,	/* 3 MHz ??? */
		new int[]{ YM2203_VOL(30,30) },
		new ReadHandlerPtr[]{ input_port_0_r },		/* DSW1 connected to port A */
		new ReadHandlerPtr[]{ input_port_1_r },		/* DSW2 connected to port B */
		new WriteHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null }
        );
	
	
	/* handler called by the 2203 emulator when the internal timers cause an IRQ */
	public static WriteYmHandlerPtr irqhandler = new WriteYmHandlerPtr() {
            public void handler(int irq) {
                cpu_set_nmi_line(2,(irq!=0) ? ASSERT_LINE : CLEAR_LINE);
            }
        };
	
	public static YM2203interface ym2203b_interface = new YM2203interface
	(
		1,			/* 1 chip */
		3000000,	/* 3 MHz ??? */
		new int[]{ YM2203_VOL(100,100) },
		new ReadHandlerPtr[]{ null },
		new ReadHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteYmHandlerPtr[]{ irqhandler }
        );
	
	public static YM2203interface kageki_ym2203_interface = new YM2203interface
	(
		1,					/* 1 chip */
		3000000,				/* 12000000/4 ??? */
		new int[]{ YM2203_VOL(35, 15) },
		new ReadHandlerPtr[]{ kageki_csport_r },
		new ReadHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ kageki_csport_w }
        );
	
	static Samplesinterface samples_interface = new Samplesinterface
        (
		1,					/* 1 channel */
		100,					/* volume */
                new String[]{null}
        );
	
	static CustomSound_interface custom_interface = new CustomSound_interface
	(
		kageki_init_samples,
		null,
		null
	);
	
	
	static MachineDriver machine_driver_arknoid2 = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				8000000,	/* ?? Hz (only crystal is 12MHz) */
							/* 8MHz is wrong, but extrmatn doesn't work properly at 6MHz */
				readmem,writemem,null,null,
				tnzs_interrupt,1
			),
			new MachineCPU(
				CPU_Z80,
				6000000,	/* ?? Hz */
				sub_readmem,sub_writemem,null,null,
				interrupt,1
			),
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,		/* video frequency (Hz), duration */
		100,							/* cpu slices */
		tnzs_init_machine,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		arknoid2_gfxdecodeinfo,
		512, 0,
		arknoid2_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		tnzs_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			)
		}
                
	);
	
	static MachineDriver machine_driver_drtoppel = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				12000000/2,		/* 6.0 MHz ??? - Main board Crystal is 12MHz */
				readmem,writemem,null,null,
				tnzs_interrupt,1
			),
			new MachineCPU(
				CPU_Z80,
				12000000/2,		/* 6.0 MHz ??? - Main board Crystal is 12MHz */
				sub_readmem,sub_writemem,null,null,
				interrupt,1
			),
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,		/* video frequency (Hz), duration */
		100,							/* cpu slices */
		tnzs_init_machine,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		tnzs_gfxdecodeinfo,
		512, 0,
		arknoid2_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		tnzs_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			)
		}
                
	);
	
	static MachineDriver machine_driver_tnzs = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				12000000/2,		/* 6.0 MHz ??? - Main board Crystal is 12MHz */
				readmem,writemem,null,null,
				tnzs_interrupt,1
			),
			new MachineCPU(
				CPU_Z80,
				12000000/2,		/* 6.0 MHz ??? - Main board Crystal is 12MHz */
				sub_readmem,sub_writemem,null,null,
				interrupt,1
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		200,	/* 100 CPU slices per frame - an high value to ensure proper */
				/* synchronization of the CPUs */
		tnzs_init_machine,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		tnzs_gfxdecodeinfo,
		512, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		tnzs_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			)
		}
                
	);
	
	static MachineDriver machine_driver_tnzsb = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				6000000,		/* 6 MHz(?) */
				readmem,writemem,null,null,
				tnzs_interrupt,1
			),
			new MachineCPU(
				CPU_Z80,
				6000000,		/* 6 MHz(?) */
				tnzsb_readmem1,tnzsb_writemem1,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80,
				4000000,		/* 4 MHz??? */
				tnzsb_readmem2,tnzsb_writemem2,tnzsb_readport,tnzsb_writeport,
				ignore_interrupt,1
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		200,	/* 100 CPU slices per frame - an high value to ensure proper */
				/* synchronization of the CPUs */
		tnzs_init_machine,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		tnzs_gfxdecodeinfo,
		512, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		tnzs_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203b_interface
			)
		}
                
	);
	
	static MachineDriver machine_driver_insectx = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				6000000,	/* 6 MHz(?) */
				readmem,writemem,null,null,
				tnzs_interrupt,1
			),
			new MachineCPU(
				CPU_Z80,
				6000000,	/* 6 MHz(?) */
				sub_readmem,sub_writemem,null,null,
				interrupt,1
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		200,	/* 100 CPU slices per frame - an high value to ensure proper */
				/* synchronization of the CPUs */
		tnzs_init_machine,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		insectx_gfxdecodeinfo,
		512, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		tnzs_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			)
		}
                
	);
	
	static MachineDriver machine_driver_kageki = new MachineDriver
	(
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				6000000,		/* 12000000/2 ??? */
				readmem, writemem, null, null,
				tnzs_interrupt, 1
			),
			new MachineCPU(
				CPU_Z80,
				4000000,		/* 12000000/3 ??? */
				kageki_sub_readmem, kageki_sub_writemem, null, null,
				interrupt, 1
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		200,	/* 200 CPU slices per frame - an high value to ensure proper */
			/* synchronization of the CPUs */
		tnzs_init_machine,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		tnzs_gfxdecodeinfo,
		512, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		tnzs_vh_screenrefresh,
	
		/* sound hardware */
		0, 0, 0, 0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				kageki_ym2203_interface
			),
			new MachineSound(
				SOUND_SAMPLES,
				samples_interface
			),
			new MachineSound(
				SOUND_CUSTOM,
				custom_interface
			)
		}
       
	);
	
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_plumppop = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* 64k + bankswitch areas for the first CPU */
		ROM_LOAD( "a98-09.bin", 0x00000, 0x08000, 0x107f9e06 );
		ROM_CONTINUE(           0x18000, 0x08000 );			/* banked at 8000-bfff */
		ROM_LOAD( "a98-10.bin", 0x20000, 0x10000, 0xdf6e6af2 );/* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "a98-11.bin", 0x00000, 0x08000, 0xbc56775c );
		ROM_CONTINUE(           0x10000, 0x08000 );	/* banked at 8000-9fff */
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "a98-01.bin", 0x00000, 0x10000, 0xf3033dca );
		ROM_RELOAD(             0x10000, 0x10000 );
		ROM_LOAD( "a98-02.bin", 0x20000, 0x10000, 0xf2d17b0c );
		ROM_RELOAD(             0x30000, 0x10000 );
		ROM_LOAD( "a98-03.bin", 0x40000, 0x10000, 0x1a519b0a );
		ROM_RELOAD(             0x40000, 0x10000 );
		ROM_LOAD( "a98-04.bin", 0x60000, 0x10000, 0xb64501a1 );
		ROM_RELOAD(             0x70000, 0x10000 );
		ROM_LOAD( "a98-05.bin", 0x80000, 0x10000, 0x45c36963 );
		ROM_RELOAD(             0x90000, 0x10000 );
		ROM_LOAD( "a98-06.bin", 0xa0000, 0x10000, 0xe075341b );
		ROM_RELOAD(             0xb0000, 0x10000 );
		ROM_LOAD( "a98-07.bin", 0xc0000, 0x10000, 0x8e16cd81 );
		ROM_RELOAD(             0xd0000, 0x10000 );
		ROM_LOAD( "a98-08.bin", 0xe0000, 0x10000, 0xbfa7609a );
		ROM_RELOAD(             0xf0000, 0x10000 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );	/* color proms */
		ROM_LOAD( "a98-13.bpr", 0x0000, 0x200, 0x7cde2da5 );/* hi bytes */
		ROM_LOAD( "a98-12.bpr", 0x0200, 0x200, 0x90dc9da7 );/* lo bytes */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_extrmatn = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );			/* Region 0 - main cpu */
		ROM_LOAD( "b06-20.bin", 0x00000, 0x08000, 0x04e3fc1f );
		ROM_CONTINUE(           0x18000, 0x08000 );			/* banked at 8000-bfff */
		ROM_LOAD( "b06-21.bin", 0x20000, 0x10000, 0x1614d6a2 );/* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );			/* Region 2 - sound cpu */
		ROM_LOAD( "b06-06.bin", 0x00000, 0x08000, 0x744f2c84 );
		ROM_CONTINUE(           0x10000, 0x08000 );/* banked at 8000-9fff */
	
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b06-01.bin", 0x00000, 0x20000, 0xd2afbf7e );
		ROM_LOAD( "b06-02.bin", 0x20000, 0x20000, 0xe0c2757a );
		ROM_LOAD( "b06-03.bin", 0x40000, 0x20000, 0xee80ab9d );
		ROM_LOAD( "b06-04.bin", 0x60000, 0x20000, 0x3697ace4 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "b06-09.bin", 0x00000, 0x200, 0xf388b361 );/* hi bytes */
		ROM_LOAD( "b06-08.bin", 0x00200, 0x200, 0x10c9aac3 );/* lo bytes */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_arknoid2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );			/* Region 0 - main cpu */
		ROM_LOAD( "b08_05.11c",	0x00000, 0x08000, 0x136edf9d );
		ROM_CONTINUE(			0x18000, 0x08000 );		/* banked at 8000-bfff */
		/* 20000-2ffff empty */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );			/* Region 2 - sound cpu */
		ROM_LOAD( "b08_13.3e",	0x00000, 0x08000, 0xe8035ef1 );
		ROM_CONTINUE(			0x10000, 0x08000 );		/* banked at 8000-9fff */
	
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b08-01.13a",	0x00000, 0x20000, 0x2ccc86b4 );
		ROM_LOAD( "b08-02.10a",	0x20000, 0x20000, 0x056a985f );
		ROM_LOAD( "b08-03.7a",	0x40000, 0x20000, 0x274a795f );
		ROM_LOAD( "b08-04.4a",	0x60000, 0x20000, 0x9754f703 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "b08-08.15f",	0x00000, 0x200, 0xa4f7ebd9 );/* hi bytes */
		ROM_LOAD( "b08-07.16f",	0x00200, 0x200, 0xea34d9f7 );/* lo bytes */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_arknid2u = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );			/* Region 0 - main cpu */
		ROM_LOAD( "b08_11.11c",	0x00000, 0x08000, 0x99555231 );
		ROM_CONTINUE(			0x18000, 0x08000 );		/* banked at 8000-bfff */
		/* 20000-2ffff empty */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );			/* Region 2 - sound cpu */
		ROM_LOAD( "b08_12.3e",	0x00000, 0x08000, 0xdc84e27d );
		ROM_CONTINUE(			0x10000, 0x08000 );		/* banked at 8000-9fff */
	
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b08-01.13a",	0x00000, 0x20000, 0x2ccc86b4 );
		ROM_LOAD( "b08-02.10a",	0x20000, 0x20000, 0x056a985f );
		ROM_LOAD( "b08-03.7a",	0x40000, 0x20000, 0x274a795f );
		ROM_LOAD( "b08-04.4a",	0x60000, 0x20000, 0x9754f703 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "b08-08.15f",	0x00000, 0x200, 0xa4f7ebd9 );/* hi bytes */
		ROM_LOAD( "b08-07.16f",	0x00200, 0x200, 0xea34d9f7 );/* lo bytes */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_arknid2j = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );			/* Region 0 - main cpu */
		ROM_LOAD( "b08_05.11c",	0x00000, 0x08000, 0x136edf9d );
		ROM_CONTINUE(			0x18000, 0x08000 );		/* banked at 8000-bfff */
		/* 20000-2ffff empty */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );			/* Region 2 - sound cpu */
		ROM_LOAD( "b08_06.3e",	0x00000, 0x08000, 0xadfcd40c );
		ROM_CONTINUE(			0x10000, 0x08000 );		/* banked at 8000-9fff */
	
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b08-01.13a",	0x00000, 0x20000, 0x2ccc86b4 );
		ROM_LOAD( "b08-02.10a",	0x20000, 0x20000, 0x056a985f );
		ROM_LOAD( "b08-03.7a",	0x40000, 0x20000, 0x274a795f );
		ROM_LOAD( "b08-04.4a",	0x60000, 0x20000, 0x9754f703 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "b08-08.15f",	0x00000, 0x200, 0xa4f7ebd9 );/* hi bytes */
		ROM_LOAD( "b08-07.16f",	0x00200, 0x200, 0xea34d9f7 );/* lo bytes */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_drtoppel = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* 64k + bankswitch areas for the first CPU */
		ROM_LOAD( "b19-09.bin", 0x00000, 0x08000, 0x3e654f82 );
		ROM_CONTINUE(           0x18000, 0x08000 );			/* banked at 8000-bfff */
		ROM_LOAD( "b19-10.bin", 0x20000, 0x10000, 0x7e72fd25 );/* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "b19-11.bin", 0x00000, 0x08000, 0x524dc249 );
		ROM_CONTINUE(           0x10000, 0x08000 );	/* banked at 8000-9fff */
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b19-01.bin", 0x00000, 0x20000, 0xa7e8a0c1 );
		ROM_LOAD( "b19-02.bin", 0x20000, 0x20000, 0x790ae654 );
		ROM_LOAD( "b19-03.bin", 0x40000, 0x20000, 0x495c4c5a );
		ROM_LOAD( "b19-04.bin", 0x60000, 0x20000, 0x647007a0 );
		ROM_LOAD( "b19-05.bin", 0x80000, 0x20000, 0x49f2b1a5 );
		ROM_LOAD( "b19-06.bin", 0xa0000, 0x20000, 0x2d39f1d0 );
		ROM_LOAD( "b19-07.bin", 0xc0000, 0x20000, 0x8bb06f41 );
		ROM_LOAD( "b19-08.bin", 0xe0000, 0x20000, 0x3584b491 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );	/* color proms */
		ROM_LOAD( "b19-13.bin", 0x0000, 0x200, 0x6a547980 );/* hi bytes */
		ROM_LOAD( "b19-12.bin", 0x0200, 0x200, 0x5754e9d8 );/* lo bytes */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_kageki = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );
		ROM_LOAD( "b35-16.11c",  0x00000, 0x08000, 0xa4e6fd58 );/* US ver */
		ROM_CONTINUE(            0x18000, 0x08000 );
		ROM_LOAD( "b35-10.9c",   0x20000, 0x10000, 0xb150457d );
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "b35-17.43e",  0x00000, 0x08000, 0xfdd9c246 );/* US ver */
		ROM_CONTINUE(            0x10000, 0x08000 );
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b35-01.13a",  0x00000, 0x20000, 0x01d83a69 );
		ROM_LOAD( "b35-02.12a",  0x20000, 0x20000, 0xd8af47ac );
		ROM_LOAD( "b35-03.10a",  0x40000, 0x20000, 0x3cb68797 );
		ROM_LOAD( "b35-04.8a",   0x60000, 0x20000, 0x71c03f91 );
		ROM_LOAD( "b35-05.7a",   0x80000, 0x20000, 0xa4e20c08 );
		ROM_LOAD( "b35-06.5a",   0xa0000, 0x20000, 0x3f8ab658 );
		ROM_LOAD( "b35-07.4a",   0xc0000, 0x20000, 0x1b4af049 );
		ROM_LOAD( "b35-08.2a",   0xe0000, 0x20000, 0xdeb2268c );
	
		ROM_REGION( 0x10000, REGION_SOUND1, 0 );/* samples */
		ROM_LOAD( "b35-15.98g",  0x00000, 0x10000, 0xe6212a0f );/* US ver */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_kagekij = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );
		ROM_LOAD( "b35-09j.11c", 0x00000, 0x08000, 0x829637d5 );/* JP ver */
		ROM_CONTINUE(            0x18000, 0x08000 );
		ROM_LOAD( "b35-10.9c",   0x20000, 0x10000, 0xb150457d );
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "b35-11j.43e", 0x00000, 0x08000, 0x64d093fc );/* JP ver */
		ROM_CONTINUE(            0x10000, 0x08000 );
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b35-01.13a",  0x00000, 0x20000, 0x01d83a69 );
		ROM_LOAD( "b35-02.12a",  0x20000, 0x20000, 0xd8af47ac );
		ROM_LOAD( "b35-03.10a",  0x40000, 0x20000, 0x3cb68797 );
		ROM_LOAD( "b35-04.8a",   0x60000, 0x20000, 0x71c03f91 );
		ROM_LOAD( "b35-05.7a",   0x80000, 0x20000, 0xa4e20c08 );
		ROM_LOAD( "b35-06.5a",   0xa0000, 0x20000, 0x3f8ab658 );
		ROM_LOAD( "b35-07.4a",   0xc0000, 0x20000, 0x1b4af049 );
		ROM_LOAD( "b35-08.2a",   0xe0000, 0x20000, 0xdeb2268c );
	
		ROM_REGION( 0x10000, REGION_SOUND1, 0 );/* samples */
		ROM_LOAD( "b35-12j.98g", 0x00000, 0x10000, 0x184409f1 );/* JP ver */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_chukatai = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* 64k + bankswitch areas for the first CPU */
		ROM_LOAD( "b44.10", 0x00000, 0x08000, 0x8c69e008 );
		ROM_CONTINUE(       0x18000, 0x08000 );			/* banked at 8000-bfff */
		ROM_LOAD( "b44.11", 0x20000, 0x10000, 0x32484094 ); /* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "b44.12", 0x00000, 0x08000, 0x0600ace6 );
		ROM_CONTINUE(       0x10000, 0x08000 );/* banked at 8000-9fff */
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "b44-01.a13", 0x00000, 0x20000, 0xaae7b3d5 );
		ROM_LOAD( "b44-02.a12", 0x20000, 0x20000, 0x7f0b9568 );
		ROM_LOAD( "b44-03.a10", 0x40000, 0x20000, 0x5a54a3b9 );
		ROM_LOAD( "b44-04.a08", 0x60000, 0x20000, 0x3c5f544b );
		ROM_LOAD( "b44-05.a07", 0x80000, 0x20000, 0xd1b7e314 );
		ROM_LOAD( "b44-06.a05", 0xa0000, 0x20000, 0x269978a8 );
		ROM_LOAD( "b44-07.a04", 0xc0000, 0x20000, 0x3e0e737e );
		ROM_LOAD( "b44-08.a02", 0xe0000, 0x20000, 0x6cb1e8fc );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_tnzs = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* 64k + bankswitch areas for the first CPU */
		ROM_LOAD( "b53_10.32",	0x00000, 0x08000, 0xa73745c6 );
		ROM_CONTINUE(			0x18000, 0x18000 );	/* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "b53_11.38",	0x00000, 0x08000, 0x9784d443 );
		ROM_CONTINUE(			0x10000, 0x08000 );	/* banked at 8000-9fff */
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		/* ROMs taken from another set (the ones from this set were read incorrectly) */
		ROM_LOAD( "b53-08.8",	0x00000, 0x20000, 0xc3519c2a );
		ROM_LOAD( "b53-07.7",	0x20000, 0x20000, 0x2bf199e8 );
		ROM_LOAD( "b53-06.6",	0x40000, 0x20000, 0x92f35ed9 );
		ROM_LOAD( "b53-05.5",	0x60000, 0x20000, 0xedbb9581 );
		ROM_LOAD( "b53-04.4",	0x80000, 0x20000, 0x59d2aef6 );
		ROM_LOAD( "b53-03.3",	0xa0000, 0x20000, 0x74acfb9b );
		ROM_LOAD( "b53-02.2",	0xc0000, 0x20000, 0x095d0dc0 );
		ROM_LOAD( "b53-01.1",	0xe0000, 0x20000, 0x9800c54d );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_tnzsb = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* 64k + bankswitch areas for the first CPU */
		ROM_LOAD( "nzsb5324.bin", 0x00000, 0x08000, 0xd66824c6 );
		ROM_CONTINUE(             0x18000, 0x18000 );	/* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "nzsb5325.bin", 0x00000, 0x08000, 0xd6ac4e71 );
		ROM_CONTINUE(             0x10000, 0x08000 );	/* banked at 8000-9fff */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );/* 64k for the third CPU */
		ROM_LOAD( "nzsb5326.bin", 0x00000, 0x10000, 0xcfd5649c );
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		/* ROMs taken from another set (the ones from this set were read incorrectly) */
		ROM_LOAD( "b53-08.8",	0x00000, 0x20000, 0xc3519c2a );
		ROM_LOAD( "b53-07.7",	0x20000, 0x20000, 0x2bf199e8 );
		ROM_LOAD( "b53-06.6",	0x40000, 0x20000, 0x92f35ed9 );
		ROM_LOAD( "b53-05.5",	0x60000, 0x20000, 0xedbb9581 );
		ROM_LOAD( "b53-04.4",	0x80000, 0x20000, 0x59d2aef6 );
		ROM_LOAD( "b53-03.3",	0xa0000, 0x20000, 0x74acfb9b );
		ROM_LOAD( "b53-02.2",	0xc0000, 0x20000, 0x095d0dc0 );
		ROM_LOAD( "b53-01.1",	0xe0000, 0x20000, 0x9800c54d );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_tnzs2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* 64k + bankswitch areas for the first CPU */
		ROM_LOAD( "ns_c-11.rom",  0x00000, 0x08000, 0x3c1dae7b );
		ROM_CONTINUE(             0x18000, 0x18000 );	/* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "ns_e-3.rom",   0x00000, 0x08000, 0xc7662e96 );
		ROM_CONTINUE(             0x10000, 0x08000 );
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "ns_a13.rom",   0x00000, 0x20000, 0x7e0bd5bb );
		ROM_LOAD( "ns_a12.rom",   0x20000, 0x20000, 0x95880726 );
		ROM_LOAD( "ns_a10.rom",   0x40000, 0x20000, 0x2bc4c053 );
		ROM_LOAD( "ns_a08.rom",   0x60000, 0x20000, 0x8ff8d88c );
		ROM_LOAD( "ns_a07.rom",   0x80000, 0x20000, 0x291bcaca );
		ROM_LOAD( "ns_a05.rom",   0xa0000, 0x20000, 0x6e762e20 );
		ROM_LOAD( "ns_a04.rom",   0xc0000, 0x20000, 0xe1fd1b9d );
		ROM_LOAD( "ns_a02.rom",   0xe0000, 0x20000, 0x2ab06bda );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_insectx = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x30000, REGION_CPU1, 0 );/* 64k + bankswitch areas for the first CPU */
		ROM_LOAD( "insector.u32", 0x00000, 0x08000, 0x18eef387 );
		ROM_CONTINUE(             0x18000, 0x18000 );	/* banked at 8000-bfff */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );/* 64k for the second CPU */
		ROM_LOAD( "insector.u38", 0x00000, 0x08000, 0x324b28c9 );
		ROM_CONTINUE(             0x10000, 0x08000 );	/* banked at 8000-9fff */
	
		ROM_REGION( 0x100000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "insector.r15", 0x00000, 0x80000, 0xd00294b1 );
		ROM_LOAD( "insector.r16", 0x80000, 0x80000, 0xdb5a7434 );
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_plumppop	   = new GameDriver("1987"	,"plumppop"	,"tnzs.java"	,rom_plumppop,null	,machine_driver_drtoppel	,input_ports_plumppop	,init_drtoppel	,ROT0	,	"Taito Corporation", "Plump Pop (Japan)" );
	public static GameDriver driver_extrmatn	   = new GameDriver("1987"	,"extrmatn"	,"tnzs.java"	,rom_extrmatn,null	,machine_driver_arknoid2	,input_ports_extrmatn	,init_extrmatn	,ROT270	,	"[Taito] World Games", "Extermination (US)" );
	public static GameDriver driver_arknoid2	   = new GameDriver("1987"	,"arknoid2"	,"tnzs.java"	,rom_arknoid2,null	,machine_driver_arknoid2	,input_ports_arknoid2	,init_arknoid2	,ROT270	,	"Taito Corporation Japan", "Arkanoid - Revenge of DOH (World)" );
	public static GameDriver driver_arknid2u	   = new GameDriver("1987"	,"arknid2u"	,"tnzs.java"	,rom_arknid2u,driver_arknoid2	,machine_driver_arknoid2	,input_ports_arknid2u	,init_arknoid2	,ROT270	,	"Taito America Corporation (Romstar license)", "Arkanoid - Revenge of DOH (US)" );
	public static GameDriver driver_arknid2j	   = new GameDriver("1987"	,"arknid2j"	,"tnzs.java"	,rom_arknid2j,driver_arknoid2	,machine_driver_arknoid2	,input_ports_arknid2u	,init_arknoid2	,ROT270	,	"Taito Corporation", "Arkanoid - Revenge of DOH (Japan)" );
	public static GameDriver driver_drtoppel	   = new GameDriver("1987"	,"drtoppel"	,"tnzs.java"	,rom_drtoppel,null	,machine_driver_drtoppel	,input_ports_drtoppel	,init_drtoppel	,ROT90	,	"Taito Corporation", "Dr. Toppel's Tankentai (Japan)" );
	public static GameDriver driver_kageki	   = new GameDriver("1988"	,"kageki"	,"tnzs.java"	,rom_kageki,null	,machine_driver_kageki	,input_ports_kageki	,init_kageki	,ROT90	,	"Taito America Corporation (Romstar license)", "Kageki (US)" );
	public static GameDriver driver_kagekij	   = new GameDriver("1988"	,"kagekij"	,"tnzs.java"	,rom_kagekij,driver_kageki	,machine_driver_kageki	,input_ports_kageki	,init_kageki	,ROT90	,	"Taito Corporation", "Kageki (Japan)" );
	public static GameDriver driver_chukatai	   = new GameDriver("1988"	,"chukatai"	,"tnzs.java"	,rom_chukatai,null	,machine_driver_tnzs	,input_ports_chukatai	,init_chukatai	,ROT0	,	"Taito Corporation", "Chuka Taisen (Japan)" );
	public static GameDriver driver_tnzs	   = new GameDriver("1988"	,"tnzs"	,"tnzs.java"	,rom_tnzs,null	,machine_driver_tnzs	,input_ports_tnzs	,init_tnzs	,ROT0	,	"Taito Corporation", "The NewZealand Story (Japan)" );
	public static GameDriver driver_tnzsb	   = new GameDriver("1988"	,"tnzsb"	,"tnzs.java"	,rom_tnzsb,driver_tnzs	,machine_driver_tnzsb	,input_ports_tnzsb	,init_tnzs	,ROT0	,	"bootleg", "The NewZealand Story (World, bootleg)" );
	public static GameDriver driver_tnzs2	   = new GameDriver("1988"	,"tnzs2"	,"tnzs.java"	,rom_tnzs2,driver_tnzs	,machine_driver_tnzs	,input_ports_tnzs2	,init_tnzs	,ROT0	,	"Taito Corporation Japan", "The NewZealand Story 2 (World)" );
	public static GameDriver driver_insectx	   = new GameDriver("1989"	,"insectx"	,"tnzs.java"	,rom_insectx,null	,machine_driver_insectx	,input_ports_insectx	,init_insectx	,ROT0	,	"Taito Corporation Japan", "Insector X (World)" );
}
