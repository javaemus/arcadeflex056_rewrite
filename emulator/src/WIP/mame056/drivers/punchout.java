/***************************************************************************

Punch Out memory map (preliminary)
Arm Wrestling runs on about the same hardware, but the video board is different.

TODO:
- The money bag is misplaced in armwrest bonus rounds.


driver by Nicola Salmoria


main CPU:

0000-bfff ROM
c000-c3ff NVRAM
d000-d7ff RAM
d800-dfff Video RAM (info screen)
e000-e7ff Video RAM (opponent)
e800-efff Video RAM (player)
f000-f03f Background row scroll (low/high couples)
f000-ffff Video RAM (background)

memory mapped ports:
write:
dfe0-dfef ??

dff0      big sprite #1 zoom low 8 bits
dff1      big sprite #1 zoom high 4 bits
dff2      big sprite #1 x pos low 8 bits
dff3      big sprite #1 x pos high 4 bits
dff4      big sprite #1 y pos low 8 bits
dff5      big sprite #1 y pos high bit
dff6      big sprite #1 x flip (bit 0)
dff7      big sprite #1 bit 0: show on top monitor; bit 1: show on bottom monitor

dff8      big sprite #2 x pos low 8 bits
dff9      big sprite #2 x pos high bit
dffa      big sprite #2 y pos low 8 bits
dffb      big sprite #2 y pos high bit
dffc      big sprite #2 x flip (bit 0)
dffd      palette bank (bit 0 = bottom monitor bit 1 = top monitor)

I/O
read:
00        IN0
01        IN1
02        DSW0
03        DSW1 (bit 4: VLM5030 busy signal)

write:
00        to 2A03 #1 IN0 (unpopulated)
01        to 2A03 #1 IN1 (unpopulated)
02        to 2A03 #2 IN0
03        to 2A03 #2 IN1
04        to VLM5030
08        NMI enable + watchdog reset
09        watchdog reset
0a        ? latched into Z80 BUS RQ
0b        to 2A03 #1 and #2 RESET
0c        to VLM5030 RESET
0d        to VLM5030 START
0e        to VLM5030 VCU
0f        enable NVRAM ?

sound CPU:
the sound CPU is a 2A03, which is a modified 6502 with built-in input ports
and two (analog?) outputs. The input ports are memory mapped at 4016-4017;
the outputs are more complicated. The only thing I have found is that 4011
goes straight into a DAC and produces the crowd sounds, but several addresses
in the range 4000-4017 are written to. There are probably three tone generators.

0000-07ff RAM
e000-ffff ROM

read:
4016      IN0
4017      IN1

write:
4000      ? is usually ORed with 90 or 50
4001      ? usually 7f, could be associated with 4000
4002-4003 ? tone #1 freq? (bit 3 of 4003 is always 1, bits 4-7 always 0)
4004      ? is usually ORed with 90 or 50
4005      ? usually 7f, could be associated with 4004
4006-4007 ? tone #2 freq? (bit 3 of 4007 is always 1, bits 4-7 always 0)
4008      ? at one point the max value is cut at 38
400a-400b ? tone #3 freq? (bit 3 of 400b is always 1, bits 4-7 always 0)
400c      ?
400e-400f ?
4011      DAC crowd noise
4015      ?? 00 or 0f
4017      ?? always c0

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;

import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
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
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;
import static WIP2.mame056.sound.vlm5030.*;
import static WIP2.mame056.sound.vlm5030H.*;
import static WIP2.mame056.sound.nes_apu.*;
import static WIP2.mame056.sound.nes_apuH.*;
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.sound.MSM5205.*;
import static WIP2.mame056.sound.MSM5205H.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;

import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.sound.sn76477H.*;
import static WIP2.mame056.sound.sn76477.*;

import static WIP.mame056.vidhrdw.punchout.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

public class punchout
{
	
	static UBytePtr nvram = new UBytePtr();
	static int[] nvram_size = new int[2];
	
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
	
	public static ReadHandlerPtr punchout_input_3_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int data = input_port_3_r.handler(offset);
		/* bit 4 is busy pin level */
		if( VLM5030_BSY() != 0 ) data &= ~0x10;
		else data |= 0x10;
		return data;
	} };
	
	public static WriteHandlerPtr punchout_speech_reset_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		VLM5030_RST( data&0x01 );
	} };
	
	public static WriteHandlerPtr punchout_speech_st_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		VLM5030_ST( data&0x01 );
	} };
	
	public static WriteHandlerPtr punchout_speech_vcu_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		VLM5030_VCU( data & 0x01 );
	} };
	
	public static WriteHandlerPtr punchout_2a03_reset_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if ((data & 1) != 0)
			cpu_set_reset_line(1,ASSERT_LINE);
		else
			cpu_set_reset_line(1,CLEAR_LINE);
	} };
	
	static int prot_mode_sel = -1; /* Mode selector */
	static int[] prot_mem=new int[16];
	
	public static ReadHandlerPtr spunchout_prot_r  = new ReadHandlerPtr() { public int handler(int offset) {
	
		switch ( offset ) {
			case 0x00:
				if ( prot_mode_sel == 0x0a )
					return cpu_readmem16(0xd012);
	
				if ( prot_mode_sel == 0x0b || prot_mode_sel == 0x23 )
					return cpu_readmem16(0xd7c1);
	
				return prot_mem[offset];
			//break;
	
			case 0x01:
				if ( prot_mode_sel == 0x08 ) /* PC = 0x0b6a */
					return 0x00; /* under 6 */
			break;
	
			case 0x02:
				if ( prot_mode_sel == 0x0b ) /* PC = 0x0613 */
					return 0x09; /* write "JMP (HL)"code to 0d79fh */
				if ( prot_mode_sel == 0x09 ) /* PC = 0x20f9, 0x22d9 */
					return prot_mem[offset]; /* act as registers */
			break;
	
			case 0x03:
				if ( prot_mode_sel == 0x09 ) /* PC = 0x1e4c */
					return prot_mem[offset] & 0x07; /* act as registers with mask */
			break;
	
			case 0x05:
				if ( prot_mode_sel == 0x09 ) /* PC = 0x29D1 */
					return prot_mem[offset] & 0x03; /* AND 0FH -> AND 06H */
			break;
	
			case 0x06:
				if ( prot_mode_sel == 0x0b ) /* PC = 0x2dd8 */
					return 0x0a; /* E=00, HL=23E6, D = (ret and 0x0f), HL+DE = 2de6 */
	
				if ( prot_mode_sel == 0x09 ) /* PC = 0x2289 */
					return prot_mem[offset] & 0x07; /* act as registers with mask */
			break;
	
			case 0x09:
				if ( prot_mode_sel == 0x09 ) /* PC = 0x0313 */
					return ( prot_mem[15] << 4 ); /* pipe through register 0xf7 << 4 */
					/* (ret or 0x10) -> (D7DF),(D7A0) - (D7DF),(D7A0) = 0d0h(ret nc) */
			break;
	
			case 0x0a:
				if ( prot_mode_sel == 0x0b ) /* PC = 0x060a */
					return 0x05; /* write "JMP (IX)"code to 0d79eh */
				if ( prot_mode_sel == 0x09 ) /* PC = 0x1bd7 */
					return prot_mem[offset] & 0x01; /* AND 0FH -> AND 01H */
			break;
	
			case 0x0b:
				if ( prot_mode_sel == 0x09 ) /* PC = 0x2AA3 */
					return prot_mem[11] & 0x03;	/* AND 0FH -> AND 03H */
			break;
	
			case 0x0c:
				/* PC = 0x2162 */
				/* B = 0(return value) */
				return 0x00;
			case 0x0d:
				return prot_mode_sel;
			//break;
		}
	
		logerror("Read from unknown protection? port %02x ( selector = %02x )\n", offset, prot_mode_sel );
	
		return prot_mem[offset];
	} };
	
	public static WriteHandlerPtr spunchout_prot_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
	
		switch ( offset ) {
			case 0x00:
				if ( prot_mode_sel == 0x0a ) {
					cpu_writemem16(0xd012, data);
					return;
				}
	
				if ( prot_mode_sel == 0x0b || prot_mode_sel == 0x23 ) {
					cpu_writemem16(0xd7c1, data);
					return;
				}
	
				prot_mem[offset] = data;
				return;
			//break;
	
			case 0x02:
				if ( prot_mode_sel == 0x09 ) { /* PC = 0x20f7, 0x22d7 */
					prot_mem[offset] = data;
					return;
				}
			break;
	
			case 0x03:
				if ( prot_mode_sel == 0x09 ) { /* PC = 0x1e4c */
					prot_mem[offset] = data;
					return;
				}
			break;
	
			case 0x05:
				prot_mem[offset] = data;
				return;
	
			case 0x06:
				if ( prot_mode_sel == 0x09 ) { /* PC = 0x2287 */
					prot_mem[offset] = data;
					return;
				}
			break;
	
			case 0x0b:
				prot_mem[offset] = data;
				return;
	
			case 0x0d: /* PC = all over the code */
				prot_mode_sel = data;
				return;
			case 0x0f:
				prot_mem[offset] = data;
				return;
		}
	
		logerror("Wrote to unknown protection? port %02x ( %02x )\n", offset, data );
	
		prot_mem[offset] = data;
	} };
	
	public static ReadHandlerPtr spunchout_prot_0_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler(0 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_0_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 0, data );
	} };
	
	public static ReadHandlerPtr spunchout_prot_1_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 1 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_1_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 1, data );
	} };
	
	public static ReadHandlerPtr spunchout_prot_2_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 2 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_2_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 2, data );
	} };
	
	public static ReadHandlerPtr spunchout_prot_3_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 3 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_3_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 3, data );
	} };
	
	public static ReadHandlerPtr spunchout_prot_5_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 5 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_5_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 5, data );
	} };
	
	
	public static ReadHandlerPtr spunchout_prot_6_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 6 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_6_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 6, data );
	} };
	
	public static ReadHandlerPtr spunchout_prot_9_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 9 );
	} };
	
	public static ReadHandlerPtr spunchout_prot_b_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 11 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_b_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 11, data );
	} };
	
	public static ReadHandlerPtr spunchout_prot_c_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 12 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_d_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 13, data );
	} };
	
	public static ReadHandlerPtr spunchout_prot_a_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 10 );
	} };
	
	public static WriteHandlerPtr spunchout_prot_a_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 10, data );
	} };
	
	//#if 0
	public static ReadHandlerPtr spunchout_prot_f_r  = new ReadHandlerPtr() { public int handler(int offset) {
		return spunchout_prot_r.handler( 15 );
	} };
	//#endif
	
	public static WriteHandlerPtr spunchout_prot_f_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		spunchout_prot_w.handler( 15, data );
	} };
	
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0xbfff, MRA_ROM ),
		new Memory_ReadAddress( 0xc000, 0xc3ff, MRA_RAM ),
		new Memory_ReadAddress( 0xd000, 0xffff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0xbfff, MWA_ROM ),
		new Memory_WriteAddress( 0xc000, 0xc3ff, MWA_RAM, nvram, nvram_size ),
		new Memory_WriteAddress( 0xd000, 0xd7ff, MWA_RAM ),
		new Memory_WriteAddress( 0xdff0, 0xdff7, MWA_RAM, punchout_bigsprite1 ),
		new Memory_WriteAddress( 0xdff8, 0xdffc, MWA_RAM, punchout_bigsprite2 ),
		new Memory_WriteAddress( 0xdffd, 0xdffd, punchout_palettebank_w, punchout_palettebank ),
		new Memory_WriteAddress( 0xd800, 0xdfff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0xe000, 0xe7ff, punchout_bigsprite1ram_w, punchout_bigsprite1ram, punchout_bigsprite1ram_size ),
		new Memory_WriteAddress( 0xe800, 0xefff, punchout_bigsprite2ram_w, punchout_bigsprite2ram, punchout_bigsprite2ram_size ),
		new Memory_WriteAddress( 0xf000, 0xf03f, MWA_RAM, punchout_scroll ),
		new Memory_WriteAddress( 0xf000, 0xffff, punchout_videoram2_w, punchout_videoram2, punchout_videoram2_size ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static IO_ReadPort readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),	new IO_ReadPort( 0x00, 0x00, input_port_0_r ),
		new IO_ReadPort( 0x01, 0x01, input_port_1_r ),
		new IO_ReadPort( 0x02, 0x02, input_port_2_r ),
		new IO_ReadPort( 0x03, 0x03, punchout_input_3_r ),
	
		/* protection ports */
		new IO_ReadPort( 0x07, 0x07, spunchout_prot_0_r ),
		new IO_ReadPort( 0x17, 0x17, spunchout_prot_1_r ),
		new IO_ReadPort( 0x27, 0x27, spunchout_prot_2_r ),
		new IO_ReadPort( 0x37, 0x37, spunchout_prot_3_r ),
		new IO_ReadPort( 0x57, 0x57, spunchout_prot_5_r ),
		new IO_ReadPort( 0x67, 0x67, spunchout_prot_6_r ),
		new IO_ReadPort( 0x97, 0x97, spunchout_prot_9_r ),
		new IO_ReadPort( 0xa7, 0xa7, spunchout_prot_a_r ),
		new IO_ReadPort( 0xb7, 0xb7, spunchout_prot_b_r ),
		new IO_ReadPort( 0xc7, 0xc7, spunchout_prot_c_r ),
		/* new IO_ReadPort( 0xf7, 0xf7, spunchout_prot_f_r ), */
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	public static IO_WritePort writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),	new IO_WritePort( 0x00, 0x01, IOWP_NOP ),	/* the 2A03 #1 is not present */
		new IO_WritePort( 0x02, 0x02, soundlatch_w ),
		new IO_WritePort( 0x03, 0x03, soundlatch2_w ),
		new IO_WritePort( 0x04, 0x04, VLM5030_data_w ),	/* VLM5030 */
		new IO_WritePort( 0x05, 0x05, IOWP_NOP ),	/* unused */
		new IO_WritePort( 0x08, 0x08, interrupt_enable_w ),
		new IO_WritePort( 0x09, 0x09, IOWP_NOP ),	/* watchdog reset, seldom used because 08 clears the watchdog as well */
		new IO_WritePort( 0x0a, 0x0a, IOWP_NOP ),	/* ?? */
		new IO_WritePort( 0x0b, 0x0b, punchout_2a03_reset_w ),
		new IO_WritePort( 0x0c, 0x0c, punchout_speech_reset_w ),	/* VLM5030 */
		new IO_WritePort( 0x0d, 0x0d, punchout_speech_st_w    ),	/* VLM5030 */
		new IO_WritePort( 0x0e, 0x0e, punchout_speech_vcu_w   ),	/* VLM5030 */
		new IO_WritePort( 0x0f, 0x0f, IOWP_NOP ),	/* enable NVRAM ? */
	
		new IO_WritePort( 0x06, 0x06, IOWP_NOP),
	
		/* protection ports */
		new IO_WritePort( 0x07, 0x07, spunchout_prot_0_w ),
		new IO_WritePort( 0x17, 0x17, spunchout_prot_1_w ),
		new IO_WritePort( 0x27, 0x27, spunchout_prot_2_w ),
		new IO_WritePort( 0x37, 0x37, spunchout_prot_3_w ),
		new IO_WritePort( 0x57, 0x57, spunchout_prot_5_w ),
		new IO_WritePort( 0x67, 0x67, spunchout_prot_6_w ),
		new IO_WritePort( 0xa7, 0xa7, spunchout_prot_a_w ),
		new IO_WritePort( 0xb7, 0xb7, spunchout_prot_b_w ),
		new IO_WritePort( 0xd7, 0xd7, spunchout_prot_d_w ),
		new IO_WritePort( 0xf7, 0xf7, spunchout_prot_f_w ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x07ff, MRA_RAM ),
		new Memory_ReadAddress( 0x4016, 0x4016, soundlatch_r ),
		new Memory_ReadAddress( 0x4017, 0x4017, soundlatch2_r ),
		new Memory_ReadAddress( 0x4000, 0x4017, NESPSG_0_r ),
		new Memory_ReadAddress( 0xe000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x07ff, MWA_RAM ),
		new Memory_WriteAddress( 0x4000, 0x4017, NESPSG_0_w ),
		new Memory_WriteAddress( 0xe000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	static InputPortPtr input_ports_punchout = new InputPortPtr(){ public void handler() { 
	PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_BUTTON1 );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_BUTTON2 );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_BUTTON3 );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY );	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_COIN2 );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_START(); 	/* DSW0 */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "Easy" );	PORT_DIPSETTING(    0x01, "Medium" );	PORT_DIPSETTING(    0x02, "Hard" );	PORT_DIPSETTING(    0x03, "Hardest" );	PORT_DIPNAME( 0x0c, 0x00, "Time" );	PORT_DIPSETTING(    0x00, "Longest" );	PORT_DIPSETTING(    0x04, "Long" );	PORT_DIPSETTING(    0x08, "Short" );	PORT_DIPSETTING(    0x0c, "Shortest" );	PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, "Rematch at a Discount" );	PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x0f, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x0e, DEF_STR( "5C_1C") );
		PORT_DIPSETTING(    0x0b, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
	//	PORT_DIPSETTING(    0x03, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x08, "1 Coin/2 Credits (2 Credits/1 Play)" );
		PORT_DIPSETTING(    0x0d, "1 Coin/3 Credits (2 Credits/1 Play)" );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_2C") );
	//	PORT_DIPSETTING(    0x04, DEF_STR( "1C_2C") );
	//	PORT_DIPSETTING(    0x09, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x0a, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x0f, DEF_STR( "Free_Play") );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );/* VLM5030 busy signal */
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );	PORT_DIPNAME( 0x80, 0x00, "Copyright" );	PORT_DIPSETTING(    0x00, "Nintendo" );	PORT_DIPSETTING(    0x80, "Nintendo of America" );	PORT_START(); 
	INPUT_PORTS_END(); }}; 
	
	/* same as punchout with additional duck button */
	static InputPortPtr input_ports_spnchout = new InputPortPtr(){ public void handler() { 
	PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_BUTTON1 );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_BUTTON2 );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_BUTTON3 );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW,  IPT_BUTTON4 );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY );	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_COIN2 );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_START(); 	/* DSW0 */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "Easy" );	PORT_DIPSETTING(    0x01, "Medium" );	PORT_DIPSETTING(    0x02, "Hard" );	PORT_DIPSETTING(    0x03, "Hardest" );	PORT_DIPNAME( 0x0c, 0x00, "Time" );	PORT_DIPSETTING(    0x00, "Longest" );	PORT_DIPSETTING(    0x04, "Long" );	PORT_DIPSETTING(    0x08, "Short" );	PORT_DIPSETTING(    0x0c, "Shortest" );	PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, "Rematch at a Discount" );	PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x0f, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x08, DEF_STR( "6C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "5C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "4C_1C") );
	//	PORT_DIPSETTING(    0x09, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
	//	PORT_DIPSETTING(    0x0e, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x0d, "1 Coin/3 Credits (2 Credits/1 Play)" );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x0b, "1 Coin/2 Credits (3 Credits/1 Play)" );
		PORT_DIPSETTING(    0x05, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x0a, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x0f, DEF_STR( "Free_Play") );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );/* VLM5030 busy signal */
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );	PORT_DIPNAME( 0x80, 0x00, "Copyright" );	PORT_DIPSETTING(    0x00, "Nintendo" );	PORT_DIPSETTING(    0x80, "Nintendo of America" );	PORT_START(); 
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_armwrest = new InputPortPtr(){ public void handler() { 
	PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_BUTTON1 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT | IPF_8WAY );	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_COIN2 );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_SERVICE1 );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_START(); 	/* DSW0 */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "Easy" );	PORT_DIPSETTING(    0x01, "Medium" );	PORT_DIPSETTING(    0x02, "Hard" );	PORT_DIPSETTING(    0x03, "Hardest" );	PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unknown") );
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
		PORT_DIPNAME( 0x40, 0x00, "Rematches" );	PORT_DIPSETTING(    0x40, "3" );	PORT_DIPSETTING(    0x00, "7" );	PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x01, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );/* VLM5030 busy signal */
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNUSED );INPUT_PORTS_END(); }}; 
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		1024,	/* 1024 characters */
		2,	/* 2 bits per pixel */
		new int[] { 1024*8*8, 0 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	/* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout armwrest_charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		2048,	/* 2048 characters */
		2,	/* 2 bits per pixel */
		new int[] { 2048*8*8, 0 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	/* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout armwrest_charlayout2 = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		2048,	/* 2048 characters */
		3,	/* 3 bits per pixel */
		new int[] { 2*2048*8*8, 2048*8*8, 0 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	/* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout charlayout1 = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		8192,	/* 8192 characters */
		3,	/* 3 bits per pixel */
		new int[] { 2*8192*8*8, 8192*8*8, 0 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	/* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout charlayout2 = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		4096,	/* 4096 characters */
		2,	/* 2 bits per pixel */
		new int[] { 4096*8*8, 0 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	/* every char takes 8 consecutive bytes */
	);
	
	static GfxDecodeInfo punchout_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,                 0, 128 ),
		new GfxDecodeInfo( REGION_GFX2, 0, charlayout,             128*4, 128 ),
		new GfxDecodeInfo( REGION_GFX3, 0, charlayout1,      128*4+128*4,  64 ),
		new GfxDecodeInfo( REGION_GFX4, 0, charlayout2, 128*4+128*4+64*8, 128 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	static GfxDecodeInfo armwrest_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, armwrest_charlayout,        0, 256 ),
		new GfxDecodeInfo( REGION_GFX2, 0, armwrest_charlayout2,   256*4,  64 ),
		new GfxDecodeInfo( REGION_GFX3, 0, charlayout1,       256*4+64*8,  64 ),
		new GfxDecodeInfo( REGION_GFX4, 0, charlayout2,  256*4+64*8+64*8, 128 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	
	static NESinterface nes_interface = new NESinterface
        (
		1,
		new int[]{ REGION_CPU2 },
		new int[]{ 50 }
	);
	
	/* filename for speech sample files */
	static String punchout_sample_names[] =
	{
		"*punchout",
		"00.wav","01.wav","02.wav","03.wav","04.wav","05.wav","06.wav","07.wav",
		"08.wav","09.wav","0a.wav","0b.wav","0c.wav","0d.wav","0e.wav","0f.wav",
		"10.wav","11.wav","12.wav","13.wav","14.wav","15.wav","16.wav","17.wav",
		"18.wav","19.wav","1a.wav","1b.wav","1c.wav","1d.wav","1e.wav","1f.wav",
		"20.wav","21.wav","22.wav","23.wav","24.wav","25.wav","26.wav","27.wav",
		"28.wav","29.wav","2a.wav","2b.wav",
		null
	};
	
	static VLM5030interface vlm5030_interface = new VLM5030interface
	(
		3580000,    /* master clock */
		50,        /* volume       */
		REGION_SOUND1,	/* memory region of speech rom */
		0,          /* memory size of speech rom */
		punchout_sample_names
	);
	
	
	
	//#define MACHINE_DRIVER(NAME,GFX,COLORTABLE)											\
	//MACHINE_DRIVER( punchout, punchout, 128*4+128*4+64*8+128*4 )
        static MachineDriver machine_driver_punchout = new MachineDriver
	(																					
		/* basic machine hardware */													
		new MachineCPU[] {																				
			new MachineCPU(																			
				CPU_Z80,																
				8000000/2,	/* 4 MHz */													
				readmem,writemem,readport,writeport,									
				nmi_interrupt,1															
			),																			
			new MachineCPU(																			
				CPU_N2A03 | CPU_AUDIO_CPU, (int) N2A03_DEFAULTCLOCK,														
				sound_readmem,sound_writemem,null,null,										
				nmi_interrupt,1															
			)																			
		},																				
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */	
		1,	/* 1 CPU slice per frame - interleaving is forced when a sound command is written */	
		null,																				
																						
		/* video hardware */															
		32*8, 28*8*2, new rectangle( 0*8, 32*8-1, 0*8, 28*8*2-1 ),									
		punchout_gfxdecodeinfo,															
		1024+1, 128*4+128*4+64*8+128*4,																
		punchout_vh_convert_color_prom,													
																						
		VIDEO_TYPE_RASTER | VIDEO_DUAL_MONITOR | VIDEO_ASPECT_RATIO(4,6),				
		null,																				
		punchout_vh_start,																	
		punchout_vh_stop,																
		punchout_vh_screenrefresh,															
																						
		/* sound hardware */															
		0,0,0,0,																		
		new MachineSound[] {																				
			new MachineSound(																			
				SOUND_NES,																
				nes_interface															
			),																			
			new MachineSound(																			
				SOUND_VLM5030,															
				vlm5030_interface														
			)																			
		},																				
																						
		nvram_handler																	
	);
	
        //MACHINE_DRIVER( armwrest, armwrest, 256*4+64*8+64*8+128*4 )
	static MachineDriver machine_driver_armwrest = new MachineDriver
	(																					
		/* basic machine hardware */													
		new MachineCPU[] {																				
			new MachineCPU(																			
				CPU_Z80,																
				8000000/2,	/* 4 MHz */													
				readmem,writemem,readport,writeport,									
				nmi_interrupt,1															
			),																			
			new MachineCPU(																			
				CPU_N2A03 | CPU_AUDIO_CPU, (int) N2A03_DEFAULTCLOCK,														
				sound_readmem,sound_writemem,null,null,										
				nmi_interrupt,1															
			)																			
		},																				
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */	
		1,	/* 1 CPU slice per frame - interleaving is forced when a sound command is written */	
		null,																				
																						
		/* video hardware */															
		32*8, 28*8*2, new rectangle( 0*8, 32*8-1, 0*8, 28*8*2-1 ),									
		armwrest_gfxdecodeinfo,															
		1024+1, 256*4+64*8+64*8+128*4,																
		armwrest_vh_convert_color_prom,													
																						
		VIDEO_TYPE_RASTER | VIDEO_DUAL_MONITOR | VIDEO_ASPECT_RATIO(4,6),				
		null,																				
		armwrest_vh_start,																	
		punchout_vh_stop,																
		armwrest_vh_screenrefresh,															
																						
		/* sound hardware */															
		0,0,0,0,																		
		new MachineSound[] {																				
			new MachineSound(																			
				SOUND_NES,																
				nes_interface															
			),																			
			new MachineSound(																			
				SOUND_VLM5030,															
				vlm5030_interface														
			)																			
		},																				
																						
		nvram_handler																	
	);
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_punchout = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "chp1-c.8l",    0x0000, 0x2000, 0xa4003adc );	ROM_LOAD( "chp1-c.8k",    0x2000, 0x2000, 0x745ecf40 );	ROM_LOAD( "chp1-c.8j",    0x4000, 0x2000, 0x7a7f870e );	ROM_LOAD( "chp1-c.8h",    0x6000, 0x2000, 0x5d8123d7 );	ROM_LOAD( "chp1-c.8f",    0x8000, 0x4000, 0xc8a55ddb );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the sound CPU */
		ROM_LOAD( "chp1-c.4k",    0xe000, 0x2000, 0xcb6ef376 );
		ROM_REGION( 0x04000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-b.4c",    0x00000, 0x2000, 0xe26dc8b3 );/* chars #1 */
		ROM_LOAD( "chp1-b.4d",    0x02000, 0x2000, 0xdd1310ca );
		ROM_REGION( 0x04000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-b.4a",    0x00000, 0x2000, 0x20fb4829 );/* chars #2 */
		ROM_LOAD( "chp1-b.4b",    0x02000, 0x2000, 0xedc34594 );
		ROM_REGION( 0x30000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-v.2r",    0x00000, 0x4000, 0xbd1d4b2e );/* chars #3 */
		ROM_LOAD( "chp1-v.2t",    0x04000, 0x4000, 0xdd9a688a );	ROM_LOAD( "chp1-v.2u",    0x08000, 0x2000, 0xda6a3c4b );	/* 0a000-0bfff empty (space for 16k ROM) */
		ROM_LOAD( "chp1-v.2v",    0x0c000, 0x2000, 0x8c734a67 );	/* 0e000-0ffff empty (space for 16k ROM) */
		ROM_LOAD( "chp1-v.3r",    0x10000, 0x4000, 0x2e74ad1d );	ROM_LOAD( "chp1-v.3t",    0x14000, 0x4000, 0x630ba9fb );	ROM_LOAD( "chp1-v.3u",    0x18000, 0x2000, 0x6440321d );	/* 1a000-1bfff empty (space for 16k ROM) */
		ROM_LOAD( "chp1-v.3v",    0x1c000, 0x2000, 0xbb7b7198 );	/* 1e000-1ffff empty (space for 16k ROM) */
		ROM_LOAD( "chp1-v.4r",    0x20000, 0x4000, 0x4e5b0fe9 );	ROM_LOAD( "chp1-v.4t",    0x24000, 0x4000, 0x37ffc940 );	ROM_LOAD( "chp1-v.4u",    0x28000, 0x2000, 0x1a7521d4 );	/* 2a000-2bfff empty (space for 16k ROM) */
		/* 2c000-2ffff empty (4v doesn't exist, it is seen as a 0xff fill) */
	
		ROM_REGION( 0x10000, REGION_GFX4, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-v.6p",    0x00000, 0x2000, 0x16588f7a );/* chars #4 */
		ROM_LOAD( "chp1-v.6n",    0x02000, 0x2000, 0xdc743674 );	/* 04000-07fff empty (space for 6l and 6k) */
		ROM_LOAD( "chp1-v.8p",    0x08000, 0x2000, 0xc2db5b4e );	ROM_LOAD( "chp1-v.8n",    0x0a000, 0x2000, 0xe6af390e );	/* 0c000-0ffff empty (space for 8l and 8k) */
	
		ROM_REGION( 0x0d00, REGION_PROMS, 0 );	ROM_LOAD( "chp1-b.6e",    0x0000, 0x0200, 0xe9ca3ac6 );/* red component */
		ROM_LOAD( "chp1-b.7e",    0x0200, 0x0200, 0x47adf7a2 );/* red component */
		ROM_LOAD( "chp1-b.6f",    0x0400, 0x0200, 0x02be56ab );/* green component */
		ROM_LOAD( "chp1-b.8e",    0x0600, 0x0200, 0xb0fc15a8 );/* green component */
		ROM_LOAD( "chp1-b.7f",    0x0800, 0x0200, 0x11de55f1 );/* blue component */
		ROM_LOAD( "chp1-b.8f",    0x0a00, 0x0200, 0x1ffd894a );/* blue component */
		ROM_LOAD( "chp1-v.2d",    0x0c00, 0x0100, 0x71dc0d48 );/* timing - not used */
	
		ROM_REGION( 0x4000, REGION_SOUND1, 0 );/* 16k for the VLM5030 data */
		ROM_LOAD( "chp1-c.6p",    0x0000, 0x4000, 0xea0bbb31 );ROM_END(); }}; 
	
	static RomLoadPtr rom_spnchout = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "chs1-c.8l",    0x0000, 0x2000, 0x703b9780 );	ROM_LOAD( "chs1-c.8k",    0x2000, 0x2000, 0xe13719f6 );	ROM_LOAD( "chs1-c.8j",    0x4000, 0x2000, 0x1fa629e8 );	ROM_LOAD( "chs1-c.8h",    0x6000, 0x2000, 0x15a6c068 );	ROM_LOAD( "chs1-c.8f",    0x8000, 0x4000, 0x4ff3cdd9 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the sound CPU */
		ROM_LOAD( "chp1-c.4k",    0xe000, 0x2000, 0xcb6ef376 );
		ROM_REGION( 0x04000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "chs1-b.4c",    0x00000, 0x0800, 0x9f2ede2d );/* chars #1 */
		ROM_CONTINUE(             0x01000, 0x0800 );	ROM_CONTINUE(             0x00800, 0x0800 );	ROM_CONTINUE(             0x01800, 0x0800 );	ROM_LOAD( "chs1-b.4d",    0x02000, 0x0800, 0x143ae5c6 );	ROM_CONTINUE(             0x03000, 0x0800 );	ROM_CONTINUE(             0x02800, 0x0800 );	ROM_CONTINUE(             0x03800, 0x0800 );
		ROM_REGION( 0x04000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-b.4a",    0x00000, 0x0800, 0xc075f831 );/* chars #2 */
		ROM_CONTINUE(             0x01000, 0x0800 );	ROM_CONTINUE(             0x00800, 0x0800 );	ROM_CONTINUE(             0x01800, 0x0800 );	ROM_LOAD( "chp1-b.4b",    0x02000, 0x0800, 0xc4cc2b5a );	ROM_CONTINUE(             0x03000, 0x0800 );	ROM_CONTINUE(             0x02800, 0x0800 );	ROM_CONTINUE(             0x03800, 0x0800 );
		ROM_REGION( 0x30000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "chs1-v.2r",    0x00000, 0x4000, 0xff33405d );/* chars #3 */
		ROM_LOAD( "chs1-v.2t",    0x04000, 0x4000, 0xf507818b );	ROM_LOAD( "chs1-v.2u",    0x08000, 0x4000, 0x0995fc95 );	ROM_LOAD( "chs1-v.2v",    0x0c000, 0x2000, 0xf44d9878 );	/* 0e000-0ffff empty (space for 16k ROM) */
		ROM_LOAD( "chs1-v.3r",    0x10000, 0x4000, 0x09570945 );	ROM_LOAD( "chs1-v.3t",    0x14000, 0x4000, 0x42c6861c );	ROM_LOAD( "chs1-v.3u",    0x18000, 0x4000, 0xbf5d02dd );	ROM_LOAD( "chs1-v.3v",    0x1c000, 0x2000, 0x5673f4fc );	/* 1e000-1ffff empty (space for 16k ROM) */
		ROM_LOAD( "chs1-v.4r",    0x20000, 0x4000, 0x8e155758 );	ROM_LOAD( "chs1-v.4t",    0x24000, 0x4000, 0xb4e43448 );	ROM_LOAD( "chs1-v.4u",    0x28000, 0x4000, 0x74e0d956 );	/* 2c000-2ffff empty (4v doesn't exist, it is seen as a 0xff fill) */
	
		ROM_REGION( 0x10000, REGION_GFX4, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-v.6p",    0x00000, 0x0800, 0x75be7aae );/* chars #4 */
		ROM_CONTINUE(             0x01000, 0x0800 );	ROM_CONTINUE(             0x00800, 0x0800 );	ROM_CONTINUE(             0x01800, 0x0800 );	ROM_LOAD( "chp1-v.6n",    0x02000, 0x0800, 0xdaf74de0 );	ROM_CONTINUE(             0x03000, 0x0800 );	ROM_CONTINUE(             0x02800, 0x0800 );	ROM_CONTINUE(             0x03800, 0x0800 );	/* 04000-07fff empty (space for 6l and 6k) */
		ROM_LOAD( "chp1-v.8p",    0x08000, 0x0800, 0x4cb7ea82 );	ROM_CONTINUE(             0x09000, 0x0800 );	ROM_CONTINUE(             0x08800, 0x0800 );	ROM_CONTINUE(             0x09800, 0x0800 );	ROM_LOAD( "chp1-v.8n",    0x0a000, 0x0800, 0x1c0d09aa );	ROM_CONTINUE(             0x0b000, 0x0800 );	ROM_CONTINUE(             0x0a800, 0x0800 );	ROM_CONTINUE(             0x0b800, 0x0800 );	/* 0c000-0ffff empty (space for 8l and 8k) */
	
		ROM_REGION( 0x0d00, REGION_PROMS, 0 );	ROM_LOAD( "chs1-b.6e",    0x0000, 0x0200, 0x0ad4d727 );/* red component */
		ROM_LOAD( "chs1-b.7e",    0x0200, 0x0200, 0x9e170f64 );/* red component */
		ROM_LOAD( "chs1-b.6f",    0x0400, 0x0200, 0x86f5cfdb );/* green component */
		ROM_LOAD( "chs1-b.8e",    0x0600, 0x0200, 0x3a2e333b );/* green component */
		ROM_LOAD( "chs1-b.7f",    0x0800, 0x0200, 0x8bd406f8 );/* blue component */
		ROM_LOAD( "chs1-b.8f",    0x0a00, 0x0200, 0x1663eed7 );/* blue component */
		ROM_LOAD( "chs1-v.2d",    0x0c00, 0x0100, 0x71dc0d48 );/* timing - not used */
	
		ROM_REGION( 0x10000, REGION_SOUND1, 0 );/* 64k for the VLM5030 data */
		ROM_LOAD( "chs1-c.6p",    0x0000, 0x4000, 0xad8b64b8 );ROM_END(); }}; 
	
	static RomLoadPtr rom_spnchotj = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "chs1c8la.bin", 0x0000, 0x2000, 0xdc2a592b );	ROM_LOAD( "chs1c8ka.bin", 0x2000, 0x2000, 0xce687182 );	ROM_LOAD( "chs1-c.8j",    0x4000, 0x2000, 0x1fa629e8 );	ROM_LOAD( "chs1-c.8h",    0x6000, 0x2000, 0x15a6c068 );	ROM_LOAD( "chs1c8fa.bin", 0x8000, 0x4000, 0xf745b5d5 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the sound CPU */
		ROM_LOAD( "chp1-c.4k",    0xe000, 0x2000, 0xcb6ef376 );
		ROM_REGION( 0x04000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "b_4c_01a.bin", 0x00000, 0x2000, 0xb017e1e9 );/* chars #1 */
		ROM_LOAD( "b_4d_01a.bin", 0x02000, 0x2000, 0xe3de9d18 );
		ROM_REGION( 0x04000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-b.4a",    0x00000, 0x0800, 0xc075f831 );/* chars #2 */
		ROM_CONTINUE(             0x01000, 0x0800 );	ROM_CONTINUE(             0x00800, 0x0800 );	ROM_CONTINUE(             0x01800, 0x0800 );	ROM_LOAD( "chp1-b.4b",    0x02000, 0x0800, 0xc4cc2b5a );	ROM_CONTINUE(             0x03000, 0x0800 );	ROM_CONTINUE(             0x02800, 0x0800 );	ROM_CONTINUE(             0x03800, 0x0800 );
		ROM_REGION( 0x30000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "chs1-v.2r",    0x00000, 0x4000, 0xff33405d );/* chars #3 */
		ROM_LOAD( "chs1-v.2t",    0x04000, 0x4000, 0xf507818b );	ROM_LOAD( "chs1-v.2u",    0x08000, 0x4000, 0x0995fc95 );	ROM_LOAD( "chs1-v.2v",    0x0c000, 0x2000, 0xf44d9878 );	/* 0e000-0ffff empty (space for 16k ROM) */
		ROM_LOAD( "chs1-v.3r",    0x10000, 0x4000, 0x09570945 );	ROM_LOAD( "chs1-v.3t",    0x14000, 0x4000, 0x42c6861c );	ROM_LOAD( "chs1-v.3u",    0x18000, 0x4000, 0xbf5d02dd );	ROM_LOAD( "chs1-v.3v",    0x1c000, 0x2000, 0x5673f4fc );	/* 1e000-1ffff empty (space for 16k ROM) */
		ROM_LOAD( "chs1-v.4r",    0x20000, 0x4000, 0x8e155758 );	ROM_LOAD( "chs1-v.4t",    0x24000, 0x4000, 0xb4e43448 );	ROM_LOAD( "chs1-v.4u",    0x28000, 0x4000, 0x74e0d956 );	/* 2c000-2ffff empty (4v doesn't exist, it is seen as a 0xff fill) */
	
		ROM_REGION( 0x10000, REGION_GFX4, ROMREGION_DISPOSE );	ROM_LOAD( "chp1-v.6p",    0x00000, 0x0800, 0x75be7aae );/* chars #4 */
		ROM_CONTINUE(             0x01000, 0x0800 );	ROM_CONTINUE(             0x00800, 0x0800 );	ROM_CONTINUE(             0x01800, 0x0800 );	ROM_LOAD( "chp1-v.6n",    0x02000, 0x0800, 0xdaf74de0 );	ROM_CONTINUE(             0x03000, 0x0800 );	ROM_CONTINUE(             0x02800, 0x0800 );	ROM_CONTINUE(             0x03800, 0x0800 );	/* 04000-07fff empty (space for 6l and 6k) */
		ROM_LOAD( "chp1-v.8p",    0x08000, 0x0800, 0x4cb7ea82 );	ROM_CONTINUE(             0x09000, 0x0800 );	ROM_CONTINUE(             0x08800, 0x0800 );	ROM_CONTINUE(             0x09800, 0x0800 );	ROM_LOAD( "chp1-v.8n",    0x0a000, 0x0800, 0x1c0d09aa );	ROM_CONTINUE(             0x0b000, 0x0800 );	ROM_CONTINUE(             0x0a800, 0x0800 );	ROM_CONTINUE(             0x0b800, 0x0800 );	/* 0c000-0ffff empty (space for 8l and 8k) */
	
		ROM_REGION( 0x0d00, REGION_PROMS, 0 );	ROM_LOAD( "chs1b_6e.bpr", 0x0000, 0x0200, 0x8efd867f );/* red component */
		ROM_LOAD( "chs1-b.7e",    0x0200, 0x0200, 0x9e170f64 );/* red component */
		ROM_LOAD( "chs1b_6f.bpr", 0x0400, 0x0200, 0x279d6cbc );/* green component */
		ROM_LOAD( "chs1-b.8e",    0x0600, 0x0200, 0x3a2e333b );/* green component */
		ROM_LOAD( "chs1b_7f.bpr", 0x0800, 0x0200, 0xcad6b7ad );/* blue component */
		ROM_LOAD( "chs1-b.8f",    0x0a00, 0x0200, 0x1663eed7 );/* blue component */
		ROM_LOAD( "chs1-v.2d",    0x0c00, 0x0100, 0x71dc0d48 );/* timing - not used */
	
		ROM_REGION( 0x10000, REGION_SOUND1, 0 );/* 64k for the VLM5030 data */
		ROM_LOAD( "chs1c6pa.bin", 0x0000, 0x4000, 0xd05fb730 );ROM_END(); }}; 
	
	static RomLoadPtr rom_armwrest = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "chv1-c.8l",    0x0000, 0x2000, 0xb09764c1 );	ROM_LOAD( "chv1-c.8k",    0x2000, 0x2000, 0x0e147ff7 );	ROM_LOAD( "chv1-c.8j",    0x4000, 0x2000, 0xe7365289 );	ROM_LOAD( "chv1-c.8h",    0x6000, 0x2000, 0xa2118eec );	ROM_LOAD( "chpv-c.8f",    0x8000, 0x4000, 0x664a07c4 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for the sound CPU */
		ROM_LOAD( "chp1-c.4k",    0xe000, 0x2000, 0xcb6ef376 );/* same as Punch Out */
	
		ROM_REGION( 0x08000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "chpv-b.2e",    0x00000, 0x4000, 0x8b45f365 );/* chars #1 */
		ROM_LOAD( "chpv-b.2d",    0x04000, 0x4000, 0xb1a2850c );
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "chpv-b.2m",    0x00000, 0x4000, 0x19245b37 );/* chars #2 */
		ROM_LOAD( "chpv-b.2l",    0x04000, 0x4000, 0x46797941 );	ROM_LOAD( "chpv-b.2k",    0x08000, 0x4000, 0x24c4c26d );
		ROM_REGION( 0x30000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "chv1-v.2r",    0x00000, 0x4000, 0xd86056d9 );/* chars #3 */
		ROM_LOAD( "chv1-v.2t",    0x04000, 0x4000, 0x5ad77059 );	/* 08000-0bfff empty */
		ROM_LOAD( "chv1-v.2v",    0x0c000, 0x4000, 0xa0fd7338 );	ROM_LOAD( "chv1-v.3r",    0x10000, 0x4000, 0x690e26fb );	ROM_LOAD( "chv1-v.3t",    0x14000, 0x4000, 0xea5d7759 );	/* 18000-1bfff empty */
		ROM_LOAD( "chv1-v.3v",    0x1c000, 0x4000, 0xceb37c05 );	ROM_LOAD( "chv1-v.4r",    0x20000, 0x4000, 0xe291cba0 );	ROM_LOAD( "chv1-v.4t",    0x24000, 0x4000, 0xe01f3b59 );	/* 28000-2bfff empty */
		/* 2c000-2ffff empty (4v doesn't exist, it is seen as a 0xff fill) */
	
		ROM_REGION( 0x10000, REGION_GFX4, ROMREGION_DISPOSE );	ROM_LOAD( "chv1-v.6p",    0x00000, 0x2000, 0xd834e142 );/* chars #4 */
		/* 02000-03fff empty (space for 16k ROM) */
		/* 04000-07fff empty (space for 6l and 6k) */
		ROM_LOAD( "chv1-v.8p",    0x08000, 0x2000, 0xa2f531db );	/* 0a000-0bfff empty (space for 16k ROM) */
		/* 0c000-0ffff empty (space for 8l and 8k) */
	
		ROM_REGION( 0x0e00, REGION_PROMS, 0 );	ROM_LOAD( "chpv-b.7b",    0x0000, 0x0200, 0xdf6fdeb3 );/* red component */
		ROM_LOAD( "chpv-b.4b",    0x0200, 0x0200, 0x9d51416e );/* red component */
		ROM_LOAD( "chpv-b.7c",    0x0400, 0x0200, 0xb1da5f42 );/* green component */
		ROM_LOAD( "chpv-b.4c",    0x0600, 0x0200, 0xb8a25795 );/* green component */
		ROM_LOAD( "chpv-b.7d",    0x0800, 0x0200, 0x4ede813e );/* blue component */
		ROM_LOAD( "chpv-b.4d",    0x0a00, 0x0200, 0x474fc3b1 );/* blue component */
		ROM_LOAD( "chv1-b.3c",    0x0c00, 0x0100, 0xc3f92ea2 );/* priority encoder - not used */
		ROM_LOAD( "chpv-v.2d",    0x0d00, 0x0100, 0x71dc0d48 );/* timing - not used */
	
		ROM_REGION( 0x10000, REGION_SOUND1, 0 );/* 64k for the VLM5030 data */
		ROM_LOAD( "chv1-c.6p",    0x0000, 0x4000, 0x31b52896 );ROM_END(); }}; 
	
	
	
	public static GameDriver driver_punchout	   = new GameDriver("1984"	,"punchout"	,"punchout.java"	,rom_punchout,null	,machine_driver_punchout	,input_ports_punchout	,init_punchout	,ROT0	,	"Nintendo", "Punch-Out!!" );
	public static GameDriver driver_spnchout	   = new GameDriver("1984"	,"spnchout"	,"punchout.java"	,rom_spnchout,null	,machine_driver_punchout	,input_ports_spnchout	,init_spnchout	,ROT0	,	"Nintendo", "Super Punch-Out!!" );
	public static GameDriver driver_spnchotj	   = new GameDriver("1984"	,"spnchotj"	,"punchout.java"	,rom_spnchotj,driver_spnchout	,machine_driver_punchout	,input_ports_spnchout	,init_spnchotj	,ROT0	,	"Nintendo", "Super Punch-Out!! (Japan)" );
	public static GameDriver driver_armwrest	   = new GameDriver("1985"	,"armwrest"	,"punchout.java"	,rom_armwrest,null	,machine_driver_armwrest	,input_ports_armwrest	,init_armwrest	,ROT0	,	"Nintendo", "Arm Wrestling" );
}
