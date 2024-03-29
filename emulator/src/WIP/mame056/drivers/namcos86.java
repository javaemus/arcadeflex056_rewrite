/*******************************************************************
Rolling Thunder
(C) 1986 Namco

To Do:
-----
Remove sprite lag (watch the "bullets" signs on the walls during scrolling).
  Increasing vblank_duration does it but some sprites flicker.

Add correct dipswitches and potentially fix controls in Wonder Momo.

Notes:
-----
PCM roms sample tables:
At the beggining of each PCM sound ROM you can find a 2 byte
offset to the beggining of each sample in the rom. Since the
table is not in sequential order, it is possible that the order
of the table is actually the sound number. Each sample ends in
a 0xff mark.

*******************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP.mame056.vidhrdw.namcos86.*;
import static WIP2.mame056.sound.sn76496.*;
import static WIP2.mame056.sound.sn76496H.*;
import static WIP2.mame056.vidhrdw.generic.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.cpu.m6800.hd63701.*;
import static WIP2.mame056.cpu.m6800.m6800H.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sound._2151intf.*;
import static WIP2.mame056.sound._2151intfH.*;
import static WIP2.mame056.sound.mixerH.*;
import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.sound.namcoH.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;

public class namcos86
{
	
	/*******************************************************************/
	
	/* Sampled voices (Modified and Added by Takahiro Nogi. 1999/09/26) */
	
	/* signed/unsigned 8-bit conversion macros */
	public static int AUDIO_CONV(int A){
            return ((A)^0x80);
        }
	
	static int[] rt_totalsamples = new int[7];
	static int rt_decode_mode;
	
	
	static ShStartPtr rt_decode_sample = new ShStartPtr() {
            public int handler(MachineSound msound) {
                GameSamples samples;
		UBytePtr src=new UBytePtr(), scan=new UBytePtr(); 
                BytePtr dest=new BytePtr();
                int last=0;
		int size, n = 0, j;
		int decode_mode;
	
		j = memory_region_length(REGION_SOUND1);
		if (j == 0) return 0;	/* no samples in this game */
		else if (j == 0x80000)	/* genpeitd */
			rt_decode_mode = 1;
		else
			rt_decode_mode = 0;
	
		logerror("pcm decode mode:%dn", rt_decode_mode );
		if (rt_decode_mode != 0) {
			decode_mode = 6;
		} else {
			decode_mode = 4;
		}
	
		/* get amount of samples */
		for ( j = 0; j < decode_mode; j++ ) {
			src = new UBytePtr(memory_region(REGION_SOUND1), ( j * 0x10000 ));
			rt_totalsamples[j] = ( ( src.read(0) << 8 ) + src.read(1) ) / 2;
			n += rt_totalsamples[j];
			logerror("rt_totalsamples[%d]:%dn", j, rt_totalsamples[j] );
		}
	
		/* calculate the amount of headers needed */
		size =  n;
	
		/* allocate */
		if ( ( Machine.samples = new GameSamples( size ) ) == null )
			return 1;
	
		samples = Machine.samples;
		samples.total = n;
	
		for ( n = 0; n < samples.total; n++ ) {
			int indx, start, offs;
	
			if ( n < rt_totalsamples[0] ) {
				src = memory_region(REGION_SOUND1);
				indx = n;
			} else
				if ( ( n - rt_totalsamples[0] ) < rt_totalsamples[1] ) {
					src = new UBytePtr(memory_region(REGION_SOUND1), 0x10000);
					indx = n - rt_totalsamples[0];
				} else
					if ( ( n - ( rt_totalsamples[0] + rt_totalsamples[1] ) ) < rt_totalsamples[2] ) {
						src = new UBytePtr(memory_region(REGION_SOUND1), 0x20000);
						indx = n - ( rt_totalsamples[0] + rt_totalsamples[1] );
					} else
						if ( ( n - ( rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] ) ) < rt_totalsamples[3] ) {
							src = new UBytePtr(memory_region(REGION_SOUND1), 0x30000);
							indx = n - ( rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] );
						} else
							if ( ( n - ( rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] ) ) < rt_totalsamples[4] ) {
								src = new UBytePtr(memory_region(REGION_SOUND1), 0x40000);
								indx = n - ( rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] );
							} else
								if ( ( n - ( rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] + rt_totalsamples[4] ) ) < rt_totalsamples[5] ) {
									src = new UBytePtr(memory_region(REGION_SOUND1), 0x50000);
									indx = n - ( rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] + rt_totalsamples[4] );
								} else {
									src = new UBytePtr(memory_region(REGION_SOUND1), 0x60000);
									indx = n - ( rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] + rt_totalsamples[4] + rt_totalsamples[5] );
								}
	
			/* calculate header offset */
			offs = indx * 2;
	
			/* get sample start offset */
			start = ( src.read(offs) << 8 ) + src.read(offs+1);
	
			/* calculate the sample size */
			scan = new UBytePtr(src, start);
			size = 0;
	
			while ( scan.read() != 0xff ) {
				if ( scan.read() == 0x00 ) { /* run length encoded data start tag */
					/* get RLE size */
					size += scan.read(1) + 1;
					scan.inc(2);
				} else {
					size++;
					scan.inc();
				}
			}
	
			/* allocate sample */
			if ( ( samples.sample[n] = new GameSample( size ) ) == null )
				return 1;
	
			/* fill up the sample info */
			samples.sample[n].length = size;
			samples.sample[n].smpfreq = 6000;	/* 6 kHz */
			samples.sample[n].resolution = 8;	/* 8 bit */
	
			/* unpack sample */
			dest = new BytePtr(samples.sample[n].data);
			scan = new UBytePtr(src, start);
	
			while ( scan.read() != 0xff ) {
				if ( scan.read() == 0x00 ) { /* run length encoded data start tag */
					int i;
					for ( i = 0; i <= scan.read(1); i++ ){ /* unpack RLE */
						dest.memory[dest.offset]=(byte) last;
                                                dest.offset++;
                                        }
	
					scan.inc(2);
				} else {
					last = AUDIO_CONV( scan.read(0) );
					dest.memory[dest.offset]=(byte) last;
					scan.inc();
				}
			}
		}
	
		return 0; /* no errors */
            }
        };
	
	
	/* play voice sample (Modified and Added by Takahiro Nogi. 1999/09/26) */
	static int[] voice = new int[2];
	
	static void namco_voice_play( int offset, int data, int ch ) {
	
		if ( voice[ch] == -1 )
			sample_stop( ch );
		else
			sample_start( ch, voice[ch], 0 );
	}
	
	public static WriteHandlerPtr namco_voice0_play_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
	
		namco_voice_play(offset, data, 0);
	} };
	
	public static WriteHandlerPtr namco_voice1_play_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
	
		namco_voice_play(offset, data, 1);
	} };
	
	/* select voice sample (Modified and Added by Takahiro Nogi. 1999/09/26) */
	static void namco_voice_select( int offset, int data, int ch ) {
	
		logerror("Voice %d mode: %d select: %02xn", ch, rt_decode_mode, data );
	
		if ( data == 0 )
			sample_stop( ch );
	
		if (rt_decode_mode != 0) {
			switch ( data & 0xe0 ) {
				case 0x00:
				break;
	
				case 0x20:
					data &= 0x1f;
					data += rt_totalsamples[0];
				break;
	
				case 0x40:
					data &= 0x1f;
					data += rt_totalsamples[0] + rt_totalsamples[1];
				break;
	
				case 0x60:
					data &= 0x1f;
					data += rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2];
				break;
	
				case 0x80:
					data &= 0x1f;
					data += rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3];
				break;
	
				case 0xa0:
					data &= 0x1f;
					data += rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] + rt_totalsamples[4];
				break;
	
				case 0xc0:
					data &= 0x1f;
					data += rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] + rt_totalsamples[4] + rt_totalsamples[5];
				break;
	
				case 0xe0:
					data &= 0x1f;
					data += rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2] + rt_totalsamples[3] + rt_totalsamples[4] + rt_totalsamples[5] + rt_totalsamples[6];
				break;
			}
		} else {
			switch ( data & 0xc0 ) {
				case 0x00:
				break;
	
				case 0x40:
					data &= 0x3f;
					data += rt_totalsamples[0];
				break;
	
				case 0x80:
					data &= 0x3f;
					data += rt_totalsamples[0] + rt_totalsamples[1];
				break;
	
				case 0xc0:
					data &= 0x3f;
					data += rt_totalsamples[0] + rt_totalsamples[1] + rt_totalsamples[2];
				break;
			}
		}
	
		voice[ch] = data - 1;
	}
	
	public static WriteHandlerPtr namco_voice0_select_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
	
		namco_voice_select(offset, data, 0);
	} };
	
	public static WriteHandlerPtr namco_voice1_select_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
	
		namco_voice_select(offset, data, 1);
	} };
	/*******************************************************************/
	
	/* shared memory area with the mcu */
	static UBytePtr shared1 = new UBytePtr();
	public static ReadHandlerPtr shared1_r  = new ReadHandlerPtr() { public int handler(int offset) { return shared1.read(offset); } };
	public static WriteHandlerPtr shared1_w = new WriteHandlerPtr() {public void handler(int offset, int data) { shared1.write(offset, data); } };
	
	
	
	public static WriteHandlerPtr spriteram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		spriteram.write(offset,data);
	} };
	public static ReadHandlerPtr spriteram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return spriteram.read(offset);
	} };
	
	public static WriteHandlerPtr bankswitch1_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr base = new UBytePtr(memory_region(REGION_CPU1), 0x10000);
	
		/* if the ROM expansion module is available, don't do anything. This avoids conflict */
		/* with bankswitch1_ext_w() in wndrmomo */
		if (memory_region(REGION_USER1) != null) return;
	
		cpu_setbank(1,new UBytePtr(base, ((data & 0x03) * 0x2000)));
	} };
	
	public static WriteHandlerPtr bankswitch1_ext_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr base = new UBytePtr (memory_region(REGION_USER1));
	
		if (base == null) return;
	
		cpu_setbank(1, new UBytePtr (base, ((data & 0x1f) * 0x2000)));
	} };
	
	public static WriteHandlerPtr bankswitch2_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr base = new UBytePtr(memory_region(REGION_CPU2), 0x10000);
	
		cpu_setbank(2, new UBytePtr(base, ((data & 0x03) * 0x2000)));
	} };
	
	/* Stubs to pass the correct Dip Switch setup to the MCU */
	public static ReadHandlerPtr dsw0_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int rhi, rlo;
	
		rhi = ( readinputport( 2 ) & 0x01 ) << 4;
		rhi |= ( readinputport( 2 ) & 0x04 ) << 3;
		rhi |= ( readinputport( 2 ) & 0x10 ) << 2;
		rhi |= ( readinputport( 2 ) & 0x40 ) << 1;
	
		rlo = ( readinputport( 3 ) & 0x01 );
		rlo |= ( readinputport( 3 ) & 0x04 ) >> 1;
		rlo |= ( readinputport( 3 ) & 0x10 ) >> 2;
		rlo |= ( readinputport( 3 ) & 0x40 ) >> 3;
	
		return ~( rhi | rlo ) & 0xff; /* Active Low */
	} };
	
	public static ReadHandlerPtr dsw1_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int rhi, rlo;
	
		rhi = ( readinputport( 2 ) & 0x02 ) << 3;
		rhi |= ( readinputport( 2 ) & 0x08 ) << 2;
		rhi |= ( readinputport( 2 ) & 0x20 ) << 1;
		rhi |= ( readinputport( 2 ) & 0x80 );
	
		rlo = ( readinputport( 3 ) & 0x02 ) >> 1;
		rlo |= ( readinputport( 3 ) & 0x08 ) >> 2;
		rlo |= ( readinputport( 3 ) & 0x20 ) >> 3;
		rlo |= ( readinputport( 3 ) & 0x80 ) >> 4;
	
		return ~( rhi | rlo ) & 0xff; /* Active Low */
	} };
	
	static int[] int_enabled = new int[2];
	
	public static WriteHandlerPtr int_ack1_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int_enabled[0] = 1;
	} };
	
	public static WriteHandlerPtr int_ack2_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int_enabled[1] = 1;
	} };
	
	public static InterruptPtr namco86_interrupt1 = new InterruptPtr() { public int handler() 
	{
		if (int_enabled[0] != 0)
		{
			int_enabled[0] = 0;
			return interrupt.handler();
		}
	
		return ignore_interrupt.handler();
	} };
	
	public static InterruptPtr namco86_interrupt2 = new InterruptPtr() { public int handler() 
	{
		if (int_enabled[1] != 0)
		{
			int_enabled[1] = 0;
			return interrupt.handler();
		}
	
		return ignore_interrupt.handler();
	} };
	
	public static WriteHandlerPtr namcos86_coin_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_lockout_global_w(data & 1);
		coin_counter_w.handler(0,~data & 2);
		coin_counter_w.handler(1,~data & 4);
	} };
	
	public static WriteHandlerPtr namcos86_led_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		set_led_status(0,data & 0x08);
		set_led_status(1,data & 0x10);
	} };
	
	
	/*******************************************************************/
	
	public static Memory_ReadAddress readmem1[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x1fff, rthunder_videoram1_r ),
		new Memory_ReadAddress( 0x2000, 0x3fff, rthunder_videoram2_r ),
		new Memory_ReadAddress( 0x4000, 0x40ff, namcos1_wavedata_r ), /* PSG device, shared RAM */
		new Memory_ReadAddress( 0x4100, 0x413f, namcos1_sound_r ), /* PSG device, shared RAM */
		new Memory_ReadAddress( 0x4000, 0x43ff, shared1_r ),
		new Memory_ReadAddress( 0x4400, 0x5fff, spriteram_r ),
		new Memory_ReadAddress( 0x6000, 0x7fff, MRA_BANK1 ),
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem1[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x1fff, rthunder_videoram1_w, rthunder_videoram1 ),
		new Memory_WriteAddress( 0x2000, 0x3fff, rthunder_videoram2_w, rthunder_videoram2 ),
	
		new Memory_WriteAddress( 0x4000, 0x40ff, namcos1_wavedata_w, namco_wavedata ), /* PSG device, shared RAM */
		new Memory_WriteAddress( 0x4100, 0x413f, namcos1_sound_w, namco_soundregs ), /* PSG device, shared RAM */
		new Memory_WriteAddress( 0x4000, 0x43ff, shared1_w, shared1 ),
	
		new Memory_WriteAddress( 0x4400, 0x5fff, spriteram_w, spriteram ),
	
		new Memory_WriteAddress( 0x6000, 0x6000, namco_voice0_play_w ),
		new Memory_WriteAddress( 0x6200, 0x6200, namco_voice0_select_w ),
		new Memory_WriteAddress( 0x6400, 0x6400, namco_voice1_play_w ),
		new Memory_WriteAddress( 0x6600, 0x6600, namco_voice1_select_w ),
		new Memory_WriteAddress( 0x6800, 0x6800, bankswitch1_ext_w ),
	//	new Memory_WriteAddress( 0x6c00, 0x6c00, MWA_NOP ), /* ??? */
	//	new Memory_WriteAddress( 0x6e00, 0x6e00, MWA_NOP ), /* ??? */
	
		new Memory_WriteAddress( 0x8000, 0x8000, watchdog_reset_w ),
		new Memory_WriteAddress( 0x8400, 0x8400, int_ack1_w ), /* IRQ acknowledge */
		new Memory_WriteAddress( 0x8800, 0x8800, rthunder_tilebank_select_0_w ),
		new Memory_WriteAddress( 0x8c00, 0x8c00, rthunder_tilebank_select_1_w ),
	
		new Memory_WriteAddress( 0x9000, 0x9002, rthunder_scroll0_w ),	/* scroll + priority */
		new Memory_WriteAddress( 0x9003, 0x9003, bankswitch1_w ),
		new Memory_WriteAddress( 0x9004, 0x9006, rthunder_scroll1_w ),	/* scroll + priority */
	
		new Memory_WriteAddress( 0x9400, 0x9402, rthunder_scroll2_w ),	/* scroll + priority */
	//	new Memory_WriteAddress( 0x9403, 0x9403 ) sub CPU rom bank select would be here
		new Memory_WriteAddress( 0x9404, 0x9406, rthunder_scroll3_w ),	/* scroll + priority */
	
		new Memory_WriteAddress( 0xa000, 0xa000, rthunder_backcolor_w ),
	
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static int UNUSED = 0x4000;
	/*                     SPRITE  VIDEO1  VIDEO2  ROM     BANK    WDOG    IRQACK */
	//CPU2_MEMORY( hopmappy, UNUSED, UNUSED, UNUSED, UNUSED, UNUSED, 0x9000, UNUSED )
        //#define CPU2_MEMORY(NAME,ADDR_SPRITE,ADDR_VIDEO1,ADDR_VIDEO2,ADDR_ROM,ADDR_BANK,ADDR_WDOG,ADDR_INT)	
	public static Memory_ReadAddress hopmappy_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),										
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x03ff, MRA_RAM ),							
		new Memory_ReadAddress( UNUSED+0x0400, UNUSED+0x1fff, spriteram_r ),						
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram1_r ),				
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram2_r ),				
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, MRA_BANK2 ),								
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),													
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
        public static Memory_WriteAddress hopmappy_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x03ff, MWA_RAM ),							
		new Memory_WriteAddress( UNUSED+0x0400, UNUSED+0x1fff, spriteram_w ),						
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram1_w ),				
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram2_w ),				
	/*	new Memory_WriteAddress( ADDR_BANK+0x00, ADDR_BANK+0x02 ) layer 2 scroll registers would be here */	
		new Memory_WriteAddress( UNUSED+0x03, UNUSED+0x03, bankswitch2_w ),								
	/*	new Memory_WriteAddress( ADDR_BANK+0x04, ADDR_BANK+0x06 ) layer 3 scroll registers would be here */	
		new Memory_WriteAddress( 0x9000, 0x9000, watchdog_reset_w ),										
		new Memory_WriteAddress( UNUSED, UNUSED, int_ack2_w ),	/* IRQ acknowledge */						
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, MWA_ROM ),									
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),													
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
	//CPU2_MEMORY( skykiddx, UNUSED, UNUSED, UNUSED, UNUSED, UNUSED, 0x9000, 0x9400 )
        //#define CPU2_MEMORY(NAME,ADDR_SPRITE,ADDR_VIDEO1,ADDR_VIDEO2,ADDR_ROM,ADDR_BANK,ADDR_WDOG,ADDR_INT)	
	public static Memory_ReadAddress skykiddx_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),										
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x03ff, MRA_RAM ),							
		new Memory_ReadAddress( UNUSED+0x0400, UNUSED+0x1fff, spriteram_r ),						
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram1_r ),				
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram2_r ),				
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, MRA_BANK2 ),								
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),													
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
        public static Memory_WriteAddress skykiddx_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x03ff, MWA_RAM ),							
		new Memory_WriteAddress( UNUSED+0x0400, UNUSED+0x1fff, spriteram_w ),						
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram1_w ),				
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, rthunder_videoram2_w ),				
	/*	new Memory_WriteAddress( ADDR_BANK+0x00, ADDR_BANK+0x02 ) layer 2 scroll registers would be here */	
		new Memory_WriteAddress( UNUSED+0x03, UNUSED+0x03, bankswitch2_w ),								
	/*	new Memory_WriteAddress( ADDR_BANK+0x04, ADDR_BANK+0x06 ) layer 3 scroll registers would be here */	
		new Memory_WriteAddress( 0x9000, 0x9000, watchdog_reset_w ),										
		new Memory_WriteAddress( 0x9400, 0x9400, int_ack2_w ),	/* IRQ acknowledge */						
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, MWA_ROM ),									
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),													
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
        //CPU2_MEMORY( roishtar, 0x0000, 0x6000, 0x4000, UNUSED, UNUSED, 0xa000, 0xb000 )
        //#define CPU2_MEMORY(NAME,ADDR_SPRITE,ADDR_VIDEO1,ADDR_VIDEO2,ADDR_ROM,ADDR_BANK,ADDR_WDOG,ADDR_INT)	
	public static Memory_ReadAddress roishtar_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),										
		new Memory_ReadAddress( 0x0000+0x0000, 0x0000+0x03ff, MRA_RAM ),							
		new Memory_ReadAddress( 0x0000+0x0400, 0x0000+0x1fff, spriteram_r ),						
		new Memory_ReadAddress( 0x6000+0x0000, 0x6000+0x1fff, rthunder_videoram1_r ),				
		new Memory_ReadAddress( 0x4000+0x0000, 0x4000+0x1fff, rthunder_videoram2_r ),				
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, MRA_BANK2 ),								
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),													
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
        public static Memory_WriteAddress roishtar_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
		new Memory_WriteAddress( 0x0000+0x0000, 0x0000+0x03ff, MWA_RAM ),							
		new Memory_WriteAddress( 0x0000+0x0400, 0x0000+0x1fff, spriteram_w ),						
		new Memory_WriteAddress( 0x6000+0x0000, 0x6000+0x1fff, rthunder_videoram1_w ),				
		new Memory_WriteAddress( 0x4000+0x0000, 0x4000+0x1fff, rthunder_videoram2_w ),				
	/*	new Memory_WriteAddress( ADDR_BANK+0x00, ADDR_BANK+0x02 ) layer 2 scroll registers would be here */	
		new Memory_WriteAddress( UNUSED+0x03, UNUSED+0x03, bankswitch2_w ),								
	/*	new Memory_WriteAddress( ADDR_BANK+0x04, ADDR_BANK+0x06 ) layer 3 scroll registers would be here */	
		new Memory_WriteAddress( 0xa000, 0xa000, watchdog_reset_w ),										
		new Memory_WriteAddress( 0xb000, 0xb000, int_ack2_w ),	/* IRQ acknowledge */						
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, MWA_ROM ),									
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),													
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
        //CPU2_MEMORY( genpeitd, 0x4000, 0x0000, 0x2000, UNUSED, UNUSED, 0xb000, 0x8800 )
        //#define CPU2_MEMORY(NAME,ADDR_SPRITE,ADDR_VIDEO1,ADDR_VIDEO2,ADDR_ROM,ADDR_BANK,ADDR_WDOG,ADDR_INT)	
	public static Memory_ReadAddress genpeitd_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),										
		new Memory_ReadAddress( 0x4000+0x0000, 0x4000+0x03ff, MRA_RAM ),							
		new Memory_ReadAddress( 0x4000+0x0400, 0x4000+0x1fff, spriteram_r ),						
		new Memory_ReadAddress( 0x0000+0x0000, 0x0000+0x1fff, rthunder_videoram1_r ),				
		new Memory_ReadAddress( 0x2000+0x0000, 0x2000+0x1fff, rthunder_videoram2_r ),				
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, MRA_BANK2 ),								
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),													
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
        public static Memory_WriteAddress genpeitd_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
		new Memory_WriteAddress( 0x4000+0x0000, 0x4000+0x03ff, MWA_RAM ),							
		new Memory_WriteAddress( 0x4000+0x0400, 0x4000+0x1fff, spriteram_w ),						
		new Memory_WriteAddress( 0x0000+0x0000, 0x0000+0x1fff, rthunder_videoram1_w ),				
		new Memory_WriteAddress( 0x2000+0x0000, 0x2000+0x1fff, rthunder_videoram2_w ),				
	/*	new Memory_WriteAddress( ADDR_BANK+0x00, ADDR_BANK+0x02 ) layer 2 scroll registers would be here */	
		new Memory_WriteAddress( UNUSED+0x03, UNUSED+0x03, bankswitch2_w ),								
	/*	new Memory_WriteAddress( ADDR_BANK+0x04, ADDR_BANK+0x06 ) layer 3 scroll registers would be here */	
		new Memory_WriteAddress( 0xb000, 0xb000, watchdog_reset_w ),										
		new Memory_WriteAddress( 0x8800, 0x8800, int_ack2_w ),	/* IRQ acknowledge */						
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, MWA_ROM ),									
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),													
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
        //CPU2_MEMORY( rthunder, 0x0000, 0x2000, 0x4000, 0x6000, 0xd800, 0x8000, 0x8800 )
        //#define CPU2_MEMORY(NAME,ADDR_SPRITE,ADDR_VIDEO1,ADDR_VIDEO2,ADDR_ROM,ADDR_BANK,ADDR_WDOG,ADDR_INT)	
	public static Memory_ReadAddress rthunder_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),										
		new Memory_ReadAddress( 0x0000+0x0000, 0x0000+0x03ff, MRA_RAM ),							
		new Memory_ReadAddress( 0x0000+0x0400, 0x0000+0x1fff, spriteram_r ),						
		new Memory_ReadAddress( 0x2000+0x0000, 0x2000+0x1fff, rthunder_videoram1_r ),				
		new Memory_ReadAddress( 0x4000+0x0000, 0x4000+0x1fff, rthunder_videoram2_r ),				
		new Memory_ReadAddress( 0x6000+0x0000, 0x6000+0x1fff, MRA_BANK2 ),								
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),													
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
        public static Memory_WriteAddress rthunder_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
		new Memory_WriteAddress( 0x0000+0x0000, 0x0000+0x03ff, MWA_RAM ),							
		new Memory_WriteAddress( 0x0000+0x0400, 0x0000+0x1fff, spriteram_w ),						
		new Memory_WriteAddress( 0x2000+0x0000, 0x2000+0x1fff, rthunder_videoram1_w ),				
		new Memory_WriteAddress( 0x4000+0x0000, 0x4000+0x1fff, rthunder_videoram2_w ),				
	/*	new Memory_WriteAddress( ADDR_BANK+0x00, ADDR_BANK+0x02 ) layer 2 scroll registers would be here */	
		new Memory_WriteAddress( 0xd800+0x03, 0xd800+0x03, bankswitch2_w ),								
	/*	new Memory_WriteAddress( ADDR_BANK+0x04, ADDR_BANK+0x06 ) layer 3 scroll registers would be here */	
		new Memory_WriteAddress( 0x8000, 0x8000, watchdog_reset_w ),										
		new Memory_WriteAddress( 0x8800, 0x8800, int_ack2_w ),	/* IRQ acknowledge */						
		new Memory_WriteAddress( 0x6000+0x0000, 0x6000+0x1fff, MWA_ROM ),									
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),													
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
        //CPU2_MEMORY( wndrmomo, 0x2000, 0x4000, 0x6000, UNUSED, UNUSED, 0xc000, 0xc800 )
        //#define CPU2_MEMORY(NAME,ADDR_SPRITE,ADDR_VIDEO1,ADDR_VIDEO2,ADDR_ROM,ADDR_BANK,ADDR_WDOG,ADDR_INT)	
	public static Memory_ReadAddress wndrmomo_readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),										
		new Memory_ReadAddress( 0x2000+0x0000, 0x2000+0x03ff, MRA_RAM ),							
		new Memory_ReadAddress( 0x2000+0x0400, 0x2000+0x1fff, spriteram_r ),						
		new Memory_ReadAddress( 0x4000+0x0000, 0x4000+0x1fff, rthunder_videoram1_r ),				
		new Memory_ReadAddress( 0x6000+0x0000, 0x6000+0x1fff, rthunder_videoram2_r ),				
		new Memory_ReadAddress( UNUSED+0x0000, UNUSED+0x1fff, MRA_BANK2 ),								
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),													
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
        public static Memory_WriteAddress wndrmomo_writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
		new Memory_WriteAddress( 0x2000+0x0000, 0x2000+0x03ff, MWA_RAM ),							
		new Memory_WriteAddress( 0x2000+0x0400, 0x2000+0x1fff, spriteram_w ),						
		new Memory_WriteAddress( 0x4000+0x0000, 0x4000+0x1fff, rthunder_videoram1_w ),				
		new Memory_WriteAddress( 0x6000+0x0000, 0x6000+0x1fff, rthunder_videoram2_w ),				
	/*	new Memory_WriteAddress( ADDR_BANK+0x00, ADDR_BANK+0x02 ) layer 2 scroll registers would be here */	
		new Memory_WriteAddress( UNUSED+0x03, UNUSED+0x03, bankswitch2_w ),								
	/*	new Memory_WriteAddress( ADDR_BANK+0x04, ADDR_BANK+0x06 ) layer 3 scroll registers would be here */	
		new Memory_WriteAddress( 0xc000, 0xc000, watchdog_reset_w ),										
		new Memory_WriteAddress( 0xc800, 0xc800, int_ack2_w ),	/* IRQ acknowledge */						
		new Memory_WriteAddress( UNUSED+0x0000, UNUSED+0x1fff, MWA_ROM ),									
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),													
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	//MCU_MEMORY( hopmappy, UNUSED, 0x2000, 0x8000, 0x8800 )
        //#define MCU_MEMORY(NAME,ADDR_LOWROM,ADDR_INPUT,ADDR_UNK1,ADDR_UNK2)			
	public static Memory_ReadAddress hopmappy_mcu_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),							
		new Memory_ReadAddress( 0x0000, 0x001f, hd63701_internal_registers_r ),						
		new Memory_ReadAddress( 0x0080, 0x00ff, MRA_RAM ),											
		new Memory_ReadAddress( 0x1000, 0x10ff, namcos1_wavedata_r ), /* PSG device, shared RAM */	
		new Memory_ReadAddress( 0x1100, 0x113f, namcos1_sound_r ), /* PSG device, shared RAM */		
		new Memory_ReadAddress( 0x1000, 0x13ff, shared1_r ),											
		new Memory_ReadAddress( 0x1400, 0x1fff, MRA_RAM ),											
		new Memory_ReadAddress( 0x2000+0x00, 0x2000+0x01, YM2151_status_port_0_r ),			
		new Memory_ReadAddress( 0x2000+0x20, 0x2000+0x20, input_port_0_r ),					
		new Memory_ReadAddress( 0x2000+0x21, 0x2000+0x21, input_port_1_r ),					
		new Memory_ReadAddress( 0x2000+0x30, 0x2000+0x30, dsw0_r ),							
		new Memory_ReadAddress( 0x2000+0x31, 0x2000+0x31, dsw1_r ),							
		new Memory_ReadAddress( UNUSED, UNUSED+0x3fff, MRA_ROM ),							
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_ROM ),											
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),											
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																			
	public static Memory_WriteAddress hopmappy_mcu_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),						
		new Memory_WriteAddress( 0x0000, 0x001f, hd63701_internal_registers_w ),						
		new Memory_WriteAddress( 0x0080, 0x00ff, MWA_RAM ),											
		new Memory_WriteAddress( 0x1000, 0x10ff, namcos1_wavedata_w ), /* PSG device, shared RAM */	
		new Memory_WriteAddress( 0x1100, 0x113f, namcos1_sound_w ), /* PSG device, shared RAM */		
		new Memory_WriteAddress( 0x1000, 0x13ff, shared1_w ),											
		new Memory_WriteAddress( 0x1400, 0x1fff, MWA_RAM ),											
		new Memory_WriteAddress( 0x2000+0x00, 0x2000+0x00, YM2151_register_port_0_w ),			
		new Memory_WriteAddress( 0x2000+0x01, 0x2000+0x01, YM2151_data_port_0_w ),				
		new Memory_WriteAddress( 0x8000, 0x8000, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0x8800, 0x8800, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( UNUSED, UNUSED+0x3fff, MWA_ROM ),							
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_ROM ),											
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),											
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
	//MCU_MEMORY( skykiddx, UNUSED, 0x2000, 0x8000, 0x8800 )
        //#define MCU_MEMORY(NAME,ADDR_LOWROM,ADDR_INPUT,ADDR_UNK1,ADDR_UNK2)			
	public static Memory_ReadAddress skykiddx_mcu_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),							
		new Memory_ReadAddress( 0x0000, 0x001f, hd63701_internal_registers_r ),						
		new Memory_ReadAddress( 0x0080, 0x00ff, MRA_RAM ),											
		new Memory_ReadAddress( 0x1000, 0x10ff, namcos1_wavedata_r ), /* PSG device, shared RAM */	
		new Memory_ReadAddress( 0x1100, 0x113f, namcos1_sound_r ), /* PSG device, shared RAM */		
		new Memory_ReadAddress( 0x1000, 0x13ff, shared1_r ),											
		new Memory_ReadAddress( 0x1400, 0x1fff, MRA_RAM ),											
		new Memory_ReadAddress( 0x2000+0x00, 0x2000+0x01, YM2151_status_port_0_r ),			
		new Memory_ReadAddress( 0x2000+0x20, 0x2000+0x20, input_port_0_r ),					
		new Memory_ReadAddress( 0x2000+0x21, 0x2000+0x21, input_port_1_r ),					
		new Memory_ReadAddress( 0x2000+0x30, 0x2000+0x30, dsw0_r ),							
		new Memory_ReadAddress( 0x2000+0x31, 0x2000+0x31, dsw1_r ),							
		new Memory_ReadAddress( UNUSED, UNUSED+0x3fff, MRA_ROM ),							
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_ROM ),											
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),											
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																			
	public static Memory_WriteAddress skykiddx_mcu_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),						
		new Memory_WriteAddress( 0x0000, 0x001f, hd63701_internal_registers_w ),						
		new Memory_WriteAddress( 0x0080, 0x00ff, MWA_RAM ),											
		new Memory_WriteAddress( 0x1000, 0x10ff, namcos1_wavedata_w ), /* PSG device, shared RAM */	
		new Memory_WriteAddress( 0x1100, 0x113f, namcos1_sound_w ), /* PSG device, shared RAM */		
		new Memory_WriteAddress( 0x1000, 0x13ff, shared1_w ),											
		new Memory_WriteAddress( 0x1400, 0x1fff, MWA_RAM ),											
		new Memory_WriteAddress( 0x2000+0x00, 0x2000+0x00, YM2151_register_port_0_w ),			
		new Memory_WriteAddress( 0x2000+0x01, 0x2000+0x01, YM2151_data_port_0_w ),				
		new Memory_WriteAddress( 0x8000, 0x8000, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0x8800, 0x8800, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( UNUSED, UNUSED+0x3fff, MWA_ROM ),							
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_ROM ),											
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),											
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
	//MCU_MEMORY( roishtar, 0x0000, 0x6000, 0x8000, 0x9800 )
        //#define MCU_MEMORY(NAME,ADDR_LOWROM,ADDR_INPUT,ADDR_UNK1,ADDR_UNK2)			
	public static Memory_ReadAddress roishtar_mcu_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),							
		new Memory_ReadAddress( 0x0000, 0x001f, hd63701_internal_registers_r ),						
		new Memory_ReadAddress( 0x0080, 0x00ff, MRA_RAM ),											
		new Memory_ReadAddress( 0x1000, 0x10ff, namcos1_wavedata_r ), /* PSG device, shared RAM */	
		new Memory_ReadAddress( 0x1100, 0x113f, namcos1_sound_r ), /* PSG device, shared RAM */		
		new Memory_ReadAddress( 0x1000, 0x13ff, shared1_r ),											
		new Memory_ReadAddress( 0x1400, 0x1fff, MRA_RAM ),											
		new Memory_ReadAddress( 0x6000+0x00, 0x6000+0x01, YM2151_status_port_0_r ),			
		new Memory_ReadAddress( 0x6000+0x20, 0x6000+0x20, input_port_0_r ),					
		new Memory_ReadAddress( 0x6000+0x21, 0x6000+0x21, input_port_1_r ),					
		new Memory_ReadAddress( 0x6000+0x30, 0x6000+0x30, dsw0_r ),							
		new Memory_ReadAddress( 0x6000+0x31, 0x6000+0x31, dsw1_r ),							
		new Memory_ReadAddress( 0x0000, 0x0000+0x3fff, MRA_ROM ),							
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_ROM ),											
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),											
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																			
	public static Memory_WriteAddress roishtar_mcu_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),						
		new Memory_WriteAddress( 0x0000, 0x001f, hd63701_internal_registers_w ),						
		new Memory_WriteAddress( 0x0080, 0x00ff, MWA_RAM ),											
		new Memory_WriteAddress( 0x1000, 0x10ff, namcos1_wavedata_w ), /* PSG device, shared RAM */	
		new Memory_WriteAddress( 0x1100, 0x113f, namcos1_sound_w ), /* PSG device, shared RAM */		
		new Memory_WriteAddress( 0x1000, 0x13ff, shared1_w ),											
		new Memory_WriteAddress( 0x1400, 0x1fff, MWA_RAM ),											
		new Memory_WriteAddress( 0x6000+0x00, 0x6000+0x00, YM2151_register_port_0_w ),			
		new Memory_WriteAddress( 0x6000+0x01, 0x6000+0x01, YM2151_data_port_0_w ),				
		new Memory_WriteAddress( 0x8000, 0x8000, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0x9800, 0x9800, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0x0000, 0x0000+0x3fff, MWA_ROM ),							
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_ROM ),											
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),											
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
	//MCU_MEMORY( genpeitd, 0x4000, 0x2800, 0xa000, 0xa800 )
        //#define MCU_MEMORY(NAME,ADDR_LOWROM,ADDR_INPUT,ADDR_UNK1,ADDR_UNK2)			
	public static Memory_ReadAddress genpeitd_mcu_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),							
		new Memory_ReadAddress( 0x0000, 0x001f, hd63701_internal_registers_r ),						
		new Memory_ReadAddress( 0x0080, 0x00ff, MRA_RAM ),											
		new Memory_ReadAddress( 0x1000, 0x10ff, namcos1_wavedata_r ), /* PSG device, shared RAM */	
		new Memory_ReadAddress( 0x1100, 0x113f, namcos1_sound_r ), /* PSG device, shared RAM */		
		new Memory_ReadAddress( 0x1000, 0x13ff, shared1_r ),											
		new Memory_ReadAddress( 0x1400, 0x1fff, MRA_RAM ),											
		new Memory_ReadAddress( 0x2800+0x00, 0x2800+0x01, YM2151_status_port_0_r ),			
		new Memory_ReadAddress( 0x2800+0x20, 0x2800+0x20, input_port_0_r ),					
		new Memory_ReadAddress( 0x2800+0x21, 0x2800+0x21, input_port_1_r ),					
		new Memory_ReadAddress( 0x2800+0x30, 0x2800+0x30, dsw0_r ),							
		new Memory_ReadAddress( 0x2800+0x31, 0x2800+0x31, dsw1_r ),							
		new Memory_ReadAddress( 0x4000, 0x4000+0x3fff, MRA_ROM ),							
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_ROM ),											
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),											
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																			
	public static Memory_WriteAddress genpeitd_mcu_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),						
		new Memory_WriteAddress( 0x0000, 0x001f, hd63701_internal_registers_w ),						
		new Memory_WriteAddress( 0x0080, 0x00ff, MWA_RAM ),											
		new Memory_WriteAddress( 0x1000, 0x10ff, namcos1_wavedata_w ), /* PSG device, shared RAM */	
		new Memory_WriteAddress( 0x1100, 0x113f, namcos1_sound_w ), /* PSG device, shared RAM */		
		new Memory_WriteAddress( 0x1000, 0x13ff, shared1_w ),											
		new Memory_WriteAddress( 0x1400, 0x1fff, MWA_RAM ),											
		new Memory_WriteAddress( 0x2800+0x00, 0x2800+0x00, YM2151_register_port_0_w ),			
		new Memory_WriteAddress( 0x2800+0x01, 0x2800+0x01, YM2151_data_port_0_w ),				
		new Memory_WriteAddress( 0xa000, 0xa000, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0xa800, 0xa800, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0x4000, 0x4000+0x3fff, MWA_ROM ),							
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_ROM ),											
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),											
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
	//MCU_MEMORY( rthunder, 0x4000, 0x2000, 0xb000, 0xb800 )
        //#define MCU_MEMORY(NAME,ADDR_LOWROM,ADDR_INPUT,ADDR_UNK1,ADDR_UNK2)			
	public static Memory_ReadAddress rthunder_mcu_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),							
		new Memory_ReadAddress( 0x0000, 0x001f, hd63701_internal_registers_r ),						
		new Memory_ReadAddress( 0x0080, 0x00ff, MRA_RAM ),											
		new Memory_ReadAddress( 0x1000, 0x10ff, namcos1_wavedata_r ), /* PSG device, shared RAM */	
		new Memory_ReadAddress( 0x1100, 0x113f, namcos1_sound_r ), /* PSG device, shared RAM */		
		new Memory_ReadAddress( 0x1000, 0x13ff, shared1_r ),											
		new Memory_ReadAddress( 0x1400, 0x1fff, MRA_RAM ),											
		new Memory_ReadAddress( 0x2000+0x00, 0x2000+0x01, YM2151_status_port_0_r ),			
		new Memory_ReadAddress( 0x2000+0x20, 0x2000+0x20, input_port_0_r ),					
		new Memory_ReadAddress( 0x2000+0x21, 0x2000+0x21, input_port_1_r ),					
		new Memory_ReadAddress( 0x2000+0x30, 0x2000+0x30, dsw0_r ),							
		new Memory_ReadAddress( 0x2000+0x31, 0x2000+0x31, dsw1_r ),							
		new Memory_ReadAddress( 0x4000, 0x4000+0x3fff, MRA_ROM ),							
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_ROM ),											
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),											
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																			
	public static Memory_WriteAddress rthunder_mcu_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),						
		new Memory_WriteAddress( 0x0000, 0x001f, hd63701_internal_registers_w ),						
		new Memory_WriteAddress( 0x0080, 0x00ff, MWA_RAM ),											
		new Memory_WriteAddress( 0x1000, 0x10ff, namcos1_wavedata_w ), /* PSG device, shared RAM */	
		new Memory_WriteAddress( 0x1100, 0x113f, namcos1_sound_w ), /* PSG device, shared RAM */		
		new Memory_WriteAddress( 0x1000, 0x13ff, shared1_w ),											
		new Memory_WriteAddress( 0x1400, 0x1fff, MWA_RAM ),											
		new Memory_WriteAddress( 0x2000+0x00, 0x2000+0x00, YM2151_register_port_0_w ),			
		new Memory_WriteAddress( 0x2000+0x01, 0x2000+0x01, YM2151_data_port_0_w ),				
		new Memory_WriteAddress( 0xb000, 0xb000, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0xb800, 0xb800, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0x4000, 0x4000+0x3fff, MWA_ROM ),							
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_ROM ),											
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),											
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
	//MCU_MEMORY( wndrmomo, 0x4000, 0x3800, 0xc000, 0xc800 )
	//#define MCU_MEMORY(NAME,ADDR_LOWROM,ADDR_INPUT,ADDR_UNK1,ADDR_UNK2)			
	public static Memory_ReadAddress wndrmomo_mcu_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),							
		new Memory_ReadAddress( 0x0000, 0x001f, hd63701_internal_registers_r ),						
		new Memory_ReadAddress( 0x0080, 0x00ff, MRA_RAM ),											
		new Memory_ReadAddress( 0x1000, 0x10ff, namcos1_wavedata_r ), /* PSG device, shared RAM */	
		new Memory_ReadAddress( 0x1100, 0x113f, namcos1_sound_r ), /* PSG device, shared RAM */		
		new Memory_ReadAddress( 0x1000, 0x13ff, shared1_r ),											
		new Memory_ReadAddress( 0x1400, 0x1fff, MRA_RAM ),											
		new Memory_ReadAddress( 0x3800+0x00, 0x3800+0x01, YM2151_status_port_0_r ),			
		new Memory_ReadAddress( 0x3800+0x20, 0x3800+0x20, input_port_0_r ),					
		new Memory_ReadAddress( 0x3800+0x21, 0x3800+0x21, input_port_1_r ),					
		new Memory_ReadAddress( 0x3800+0x30, 0x3800+0x30, dsw0_r ),							
		new Memory_ReadAddress( 0x3800+0x31, 0x3800+0x31, dsw1_r ),							
		new Memory_ReadAddress( 0x4000, 0x4000+0x3fff, MRA_ROM ),							
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_ROM ),											
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),											
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																			
	public static Memory_WriteAddress wndrmomo_mcu_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),						
		new Memory_WriteAddress( 0x0000, 0x001f, hd63701_internal_registers_w ),						
		new Memory_WriteAddress( 0x0080, 0x00ff, MWA_RAM ),											
		new Memory_WriteAddress( 0x1000, 0x10ff, namcos1_wavedata_w ), /* PSG device, shared RAM */	
		new Memory_WriteAddress( 0x1100, 0x113f, namcos1_sound_w ), /* PSG device, shared RAM */		
		new Memory_WriteAddress( 0x1000, 0x13ff, shared1_w ),											
		new Memory_WriteAddress( 0x1400, 0x1fff, MWA_RAM ),											
		new Memory_WriteAddress( 0x3800+0x00, 0x3800+0x00, YM2151_register_port_0_w ),			
		new Memory_WriteAddress( 0x3800+0x01, 0x3800+0x01, YM2151_data_port_0_w ),				
		new Memory_WriteAddress( 0xc000, 0xc000, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0xc800, 0xc800, MWA_NOP ), /* ??? written (not always) at end of interrupt */	
		new Memory_WriteAddress( 0x4000, 0x4000+0x3fff, MWA_ROM ),							
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_ROM ),											
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),											
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static ReadHandlerPtr readFF  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return 0xff;
	} };
	
	public static IO_ReadPort mcu_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_ReadPort( HD63701_PORT1, HD63701_PORT1, input_port_4_r ),
		new IO_ReadPort( HD63701_PORT2, HD63701_PORT2, readFF ),	/* leds won't work otherwise */
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort mcu_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( HD63701_PORT1, HD63701_PORT1, namcos86_coin_w ),
		new IO_WritePort( HD63701_PORT2, HD63701_PORT2, namcos86_led_w ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	
	/*******************************************************************/
	
	static InputPortPtr input_ports_hopmappy = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 2 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 2 player 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BITX( 0x80, 0x80, IPT_SERVICE, "Service Switch", KEYCODE_F1, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 2 player 2 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START();       /* DSWA */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x04, 0x00, "Allow Continue" );
		PORT_DIPSETTING(    0x04, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x18, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x08, "1" );
		PORT_DIPSETTING(    0x10, "2" );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPSETTING(    0x18, "5" );
		PORT_DIPNAME( 0x60, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x60, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START();       /* DSWB */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_BITX(    0x10, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Level Select", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "Easy" );
		PORT_DIPSETTING(    0x80, "Hard" );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin lockout */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 2 */
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_COCKTAIL );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_skykiddx = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 2 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BITX( 0x80, 0x80, IPT_SERVICE, "Service Switch", KEYCODE_F1, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START();       /* DSWA */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x04, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_BITX(    0x08, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Level Select", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x60, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x60, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START();       /* DSWB */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Flip_Screen") );
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
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x20, "20000 80000" );
		PORT_DIPSETTING(    0x00, "30000 90000" );
		PORT_DIPNAME( 0xc0, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x40, "1" );
		PORT_DIPSETTING(    0x80, "2" );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPSETTING(    0xc0, "5" );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin lockout */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 2 */
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_COCKTAIL );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_roishtar = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 2 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_DOWN   | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_DOWN  | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_RIGHT | IPF_8WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_UP  | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_UP | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START();       /* DSWA */
		PORT_DIPNAME( 0x07, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x07, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_6C") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START();       /* DSWB */
		PORT_DIPNAME( 0x07, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x07, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_6C") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin lockout */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 2 */
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_LEFT  | IPF_8WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICKLEFT_RIGHT | IPF_8WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICKRIGHT_LEFT | IPF_8WAY );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_genpeitd = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 2 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BITX( 0x80, 0x80, IPT_SERVICE, "Service Switch", KEYCODE_F1, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START();       /* DSWA */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x04, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x00, "Allow Continue" );
		PORT_DIPSETTING(    0x08, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x60, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x60, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START();       /* DSWB */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x10, "Easy" );
		PORT_DIPSETTING(    0x00, "Normal" );
		PORT_DIPSETTING(    0x20, "Hard" );
		PORT_DIPSETTING(    0x30, "Hardest" );
		PORT_DIPNAME( 0xc0, 0x00, "Candle" );
		PORT_DIPSETTING(    0x40, "40" );
		PORT_DIPSETTING(    0x00, "50" );
		PORT_DIPSETTING(    0x80, "60" );
		PORT_DIPSETTING(    0xc0, "70" );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin lockout */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 2 */
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_PLAYER2 );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_rthunder = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 2 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BITX( 0x80, 0x80, IPT_SERVICE, "Service Switch", KEYCODE_F1, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START();       /* DSWA */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x04, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_BITX(    0x08, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Invulnerability", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x60, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x60, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START();       /* DSWB */
		PORT_DIPNAME( 0x01, 0x00, "Continues" );
		PORT_DIPSETTING(    0x01, "3" );
		PORT_DIPSETTING(    0x00, "6" );
		PORT_DIPNAME( 0x06, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, "Upright 1 Player" );
	/*	PORT_DIPSETTING(    0x04, "Upright 1 Player" );*/
		PORT_DIPSETTING(    0x02, "Upright 2 Players" );
		PORT_DIPSETTING(    0x06, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x08, 0x08, "Level Select" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "Normal" );
		PORT_DIPSETTING(    0x10, "Easy" );
		PORT_DIPNAME( 0x20, 0x20, "Timer value" );
		PORT_DIPSETTING(    0x00, "120 secs" );
		PORT_DIPSETTING(    0x20, "150 secs" );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "70k, 200k" );
		PORT_DIPSETTING(    0x40, "100k, 300k" );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPSETTING(    0x80, "5" );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin lockout */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 2 */
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY | IPF_PLAYER2 );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_rthundro = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 2 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BITX( 0x80, 0x80, IPT_SERVICE, "Service Switch", KEYCODE_F1, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START();       /* DSWA */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x04, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_BITX(    0x08, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Invulnerability", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x60, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x60, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START();       /* DSWB */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_BITX(    0x08, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Level Select", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0xc0, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x40, "1" );
		PORT_DIPSETTING(    0x80, "2" );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPSETTING(    0xc0, "5" );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin lockout */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 2 */
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_4WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_4WAY | IPF_PLAYER2 );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_wndrmomo = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 2 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BITX( 0x80, 0x80, IPT_SERVICE, "Service Switch", KEYCODE_F1, IP_JOY_NONE );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNKNOWN );/* button 3 player 1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_SERVICE1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNUSED );
	
		PORT_START();       /* DSWA */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x04, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x08, "Level Select" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x60, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x60, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
		PORT_SERVICE( 0x80, IP_ACTIVE_HIGH );
	
		PORT_START();       /* DSWB */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x06, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, "Type A" );
		PORT_DIPSETTING(    0x02, "Type B" );
		PORT_DIPSETTING(    0x04, "Type C" );
	//	PORT_DIPSETTING(    0x06, "Type A" );
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
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin lockout */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 1 */
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_SPECIAL );/* OUT:coin counter 2 */
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_4WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_4WAY | IPF_PLAYER2 );
	INPUT_PORTS_END(); }}; 
	
	
	/*******************************************************************/
	
	static GfxLayout tilelayout = new GfxLayout
	(
		8,8,
		RGN_FRAC(1,3),
		3,
		new int[] { RGN_FRAC(2,3), RGN_FRAC(1,3), RGN_FRAC(0,3) },
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8
	);
	
	//SPRITELAYOUT(256);
        static GfxLayout spritelayout_256 = new GfxLayout
	(																			   
		16,16,	/* 16*16 sprites */												   
		256,	/* NUM sprites */												   
		4,	/* 4 bits per pixel */												   
		new int[] { 0, 1, 2, 3 },															   
		new int[] { 0*4, 1*4, 2*4, 3*4, 4*4, 5*4, 6*4, 7*4,								   
				8*4, 9*4, 10*4, 11*4, 12*4, 13*4, 14*4, 15*4 },					   
		new int[] { 0*64, 1*64, 2*64, 3*64, 4*64, 5*64, 6*64, 7*64,						   
				8*64, 9*64, 10*64, 11*64, 12*64, 13*64, 14*64, 15*64 },			   
		16*64																	   
	);
        
	//SPRITELAYOUT(512);
        static GfxLayout spritelayout_512 = new GfxLayout
	(																			   
		16,16,	/* 16*16 sprites */												   
		512,	/* NUM sprites */												   
		4,	/* 4 bits per pixel */												   
		new int[] { 0, 1, 2, 3 },															   
		new int[] { 0*4, 1*4, 2*4, 3*4, 4*4, 5*4, 6*4, 7*4,								   
				8*4, 9*4, 10*4, 11*4, 12*4, 13*4, 14*4, 15*4 },					   
		new int[] { 0*64, 1*64, 2*64, 3*64, 4*64, 5*64, 6*64, 7*64,						   
				8*64, 9*64, 10*64, 11*64, 12*64, 13*64, 14*64, 15*64 },			   
		16*64																	   
	);
        
	//SPRITELAYOUT(1024);
        static GfxLayout spritelayout_1024 = new GfxLayout
	(																			   
		16,16,	/* 16*16 sprites */												   
		1024,	/* NUM sprites */												   
		4,	/* 4 bits per pixel */												   
		new int[] { 0, 1, 2, 3 },															   
		new int[] { 0*4, 1*4, 2*4, 3*4, 4*4, 5*4, 6*4, 7*4,								   
				8*4, 9*4, 10*4, 11*4, 12*4, 13*4, 14*4, 15*4 },					   
		new int[] { 0*64, 1*64, 2*64, 3*64, 4*64, 5*64, 6*64, 7*64,						   
				8*64, 9*64, 10*64, 11*64, 12*64, 13*64, 14*64, 15*64 },			   
		16*64																	   
	);
	
	//GFXDECODE( 256)
        static GfxDecodeInfo gfxdecodeinfo_256[] =
	{																			
		new GfxDecodeInfo( REGION_GFX1, 0x00000,      tilelayout,            2048*0, 256 ),		
		new GfxDecodeInfo( REGION_GFX2, 0x00000,      tilelayout,            2048*0, 256 ),		
		new GfxDecodeInfo( REGION_GFX3, 0*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 1*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 2*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 3*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 4*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 5*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 6*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 7*128*256, spritelayout_256, 2048*1, 128 ),		
		new GfxDecodeInfo( -1 )
	};
	
        //GFXDECODE( 512)
        static GfxDecodeInfo gfxdecodeinfo_512[] =
	{																			
		new GfxDecodeInfo( REGION_GFX1, 0x00000,      tilelayout,            2048*0, 256 ),		
		new GfxDecodeInfo( REGION_GFX2, 0x00000,      tilelayout,            2048*0, 256 ),		
		new GfxDecodeInfo( REGION_GFX3, 0*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 1*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 2*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 3*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 4*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 5*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 6*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 7*128*512, spritelayout_512, 2048*1, 128 ),		
		new GfxDecodeInfo( -1 )
	};

	//GFXDECODE(1024)
        static GfxDecodeInfo gfxdecodeinfo_1024[] =
	{																			
		new GfxDecodeInfo( REGION_GFX1, 0x00000,      tilelayout,            2048*0, 256 ),		
		new GfxDecodeInfo( REGION_GFX2, 0x00000,      tilelayout,            2048*0, 256 ),		
		new GfxDecodeInfo( REGION_GFX3, 0*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 1*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 2*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 3*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 4*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 5*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 6*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( REGION_GFX3, 7*128*1024, spritelayout_1024, 2048*1, 128 ),		
		new GfxDecodeInfo( -1 )																	
	};
	
	/*******************************************************************/
	
	static YM2151interface ym2151_interface = new YM2151interface
	(
		1,                      /* 1 chip */
		3579580,                /* 3.579580 MHz ? */
		new int[]{ YM3012_VOL(0,MIXER_PAN_CENTER,60,MIXER_PAN_CENTER) },	/* only right channel is connected */
		new WriteYmHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null }
	);
	
	static namco_interface namco_interface = new namco_interface
	(
		49152000/2048, 		/* 24000Hz */
		8,		/* number of voices */
		50,     /* playback volume */
		-1,		/* memory region */
		0		/* stereo */
	);
	
	static Samplesinterface samples_interface = new Samplesinterface
	(
		2,	/* 2 channels for voice effects */
		40,	/* volume */
                null
        );
	
	static CustomSound_interface custom_interface = new CustomSound_interface
	(
		rt_decode_sample,
		null,
		null
	);
	
	
	public static InitMachinePtr namco86_init_machine = new InitMachinePtr() { public void handler()
	{
		UBytePtr base = new UBytePtr(memory_region(REGION_CPU1), 0x10000);
	
		cpu_setbank(1,base);
	
		int_enabled[0] = int_enabled[1] = 1;
	} };
	
	//MACHINE_DRIVER( hopmappy, 256 )
        //#define MACHINE_DRIVER(NAME,GFX)												
	static MachineDriver machine_driver_hopmappy = new MachineDriver
	(																				
		new MachineCPU[] {																			
			new MachineCPU(																		
				CPU_M6809,															
				6000000/4,		/* ? */												
				/*49152000/32, rthunder doesn't work with this */					
				readmem1,writemem1,null,null,												
				namco86_interrupt1,1												
			),																		
			new MachineCPU(																		
				CPU_M6809,															
				49152000/32, 		/* ? */											
				hopmappy_readmem2,hopmappy_writemem2,null,null,								
				namco86_interrupt2,1												
			),																		
			new MachineCPU(																		
				CPU_HD63701,	/* or compatible 6808 with extra instructions */	
				49152000/32, 		/* ? */											
				hopmappy_mcu_readmem,hopmappy_mcu_writemem,mcu_readport,mcu_writeport,	
				interrupt, 1	/* ??? */											
			)																		
		},																			
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,									
		100, /* cpu slices */														
		namco86_init_machine, /* init machine */									
																					
		/* video hardware */														
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),									
		gfxdecodeinfo_256,
		512,4096,																	
		namcos86_vh_convert_color_prom,												
																					
		VIDEO_TYPE_RASTER,															
		null,																			
		namcos86_vh_start,															
		null,																			
		namcos86_vh_screenrefresh,													
																					
		/* sound hardware */														
		0,0,0,0,																	
		new MachineSound[] {																			
			new MachineSound(																		
				SOUND_YM2151,														
				ym2151_interface													
			),																		
			new MachineSound(																		
				SOUND_NAMCO,														
				namco_interface													
			),																		
			new MachineSound(																		
				SOUND_SAMPLES,														
				samples_interface													
			),																		
			new MachineSound(																		
				SOUND_CUSTOM,	/* actually initializes the samples */				
				custom_interface													
			)																		
		}																			
	);

	//MACHINE_DRIVER( skykiddx, 256 )
        //#define MACHINE_DRIVER(NAME,GFX)												
	static MachineDriver machine_driver_skykiddx = new MachineDriver
	(																				
		new MachineCPU[] {																			
			new MachineCPU(																		
				CPU_M6809,															
				6000000/4,		/* ? */												
				/*49152000/32, rthunder doesn't work with this */					
				readmem1,writemem1,null,null,												
				namco86_interrupt1,1												
			),																		
			new MachineCPU(																		
				CPU_M6809,															
				49152000/32, 		/* ? */											
				skykiddx_readmem2,skykiddx_writemem2,null,null,								
				namco86_interrupt2,1												
			),																		
			new MachineCPU(																		
				CPU_HD63701,	/* or compatible 6808 with extra instructions */	
				49152000/32, 		/* ? */											
				skykiddx_mcu_readmem,skykiddx_mcu_writemem,mcu_readport,mcu_writeport,	
				interrupt, 1	/* ??? */											
			)																		
		},																			
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,									
		100, /* cpu slices */														
		namco86_init_machine, /* init machine */									
																					
		/* video hardware */														
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),									
		gfxdecodeinfo_256,
		512,4096,																	
		namcos86_vh_convert_color_prom,												
																					
		VIDEO_TYPE_RASTER,															
		null,																			
		namcos86_vh_start,															
		null,																			
		namcos86_vh_screenrefresh,													
																					
		/* sound hardware */														
		0,0,0,0,																	
		new MachineSound[] {																			
			new MachineSound(																		
				SOUND_YM2151,														
				ym2151_interface													
			),																		
			new MachineSound(																		
				SOUND_NAMCO,														
				namco_interface													
			),																		
			new MachineSound(																		
				SOUND_SAMPLES,														
				samples_interface													
			),																		
			new MachineSound(																		
				SOUND_CUSTOM,	/* actually initializes the samples */				
				custom_interface													
			)																		
		}																			
	);

	//MACHINE_DRIVER( roishtar, 256 )
        //#define MACHINE_DRIVER(NAME,GFX)												
	static MachineDriver machine_driver_roishtar = new MachineDriver
	(																				
		new MachineCPU[] {																			
			new MachineCPU(																		
				CPU_M6809,															
				6000000/4,		/* ? */												
				/*49152000/32, rthunder doesn't work with this */					
				readmem1,writemem1,null,null,												
				namco86_interrupt1,1												
			),																		
			new MachineCPU(																		
				CPU_M6809,															
				49152000/32, 		/* ? */											
				roishtar_readmem2,roishtar_writemem2,null,null,								
				namco86_interrupt2,1												
			),																		
			new MachineCPU(																		
				CPU_HD63701,	/* or compatible 6808 with extra instructions */	
				49152000/32, 		/* ? */											
				roishtar_mcu_readmem,roishtar_mcu_writemem,mcu_readport,mcu_writeport,	
				interrupt, 1	/* ??? */											
			)																		
		},																			
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,									
		100, /* cpu slices */														
		namco86_init_machine, /* init machine */									
																					
		/* video hardware */														
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),									
		gfxdecodeinfo_256,														
		512,4096,																	
		namcos86_vh_convert_color_prom,												
																					
		VIDEO_TYPE_RASTER,															
		null,																			
		namcos86_vh_start,															
		null,																			
		namcos86_vh_screenrefresh,													
																					
		/* sound hardware */														
		0,0,0,0,																	
		new MachineSound[] {																			
			new MachineSound(																		
				SOUND_YM2151,														
				ym2151_interface													
			),																		
			new MachineSound(																		
				SOUND_NAMCO,														
				namco_interface													
			),																		
			new MachineSound(																		
				SOUND_SAMPLES,														
				samples_interface													
			),																		
			new MachineSound(																		
				SOUND_CUSTOM,	/* actually initializes the samples */				
				custom_interface													
			)																		
		}																			
	);

	//MACHINE_DRIVER( genpeitd, 1024 )
        //#define MACHINE_DRIVER(NAME,GFX)												
	static MachineDriver machine_driver_genpeitd = new MachineDriver
	(																				
		new MachineCPU[] {																			
			new MachineCPU(																		
				CPU_M6809,															
				6000000/4,		/* ? */												
				/*49152000/32, rthunder doesn't work with this */					
				readmem1,writemem1,null,null,												
				namco86_interrupt1,1												
			),																		
			new MachineCPU(																		
				CPU_M6809,															
				49152000/32, 		/* ? */											
				genpeitd_readmem2,genpeitd_writemem2,null,null,								
				namco86_interrupt2,1												
			),																		
			new MachineCPU(																		
				CPU_HD63701,	/* or compatible 6808 with extra instructions */	
				49152000/32, 		/* ? */											
				genpeitd_mcu_readmem,genpeitd_mcu_writemem,mcu_readport,mcu_writeport,	
				interrupt, 1	/* ??? */											
			)																		
		},																			
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,									
		100, /* cpu slices */														
		namco86_init_machine, /* init machine */									
																					
		/* video hardware */														
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),									
		gfxdecodeinfo_1024,														
		512,4096,																	
		namcos86_vh_convert_color_prom,												
																					
		VIDEO_TYPE_RASTER,															
		null,																			
		namcos86_vh_start,															
		null,																			
		namcos86_vh_screenrefresh,													
																					
		/* sound hardware */														
		0,0,0,0,																	
		new MachineSound[] {																			
			new MachineSound(																		
				SOUND_YM2151,														
				ym2151_interface													
			),																		
			new MachineSound(																		
				SOUND_NAMCO,														
				namco_interface													
			),																		
			new MachineSound(																		
				SOUND_SAMPLES,														
				samples_interface													
			),																		
			new MachineSound(																		
				SOUND_CUSTOM,	/* actually initializes the samples */				
				custom_interface													
			)																		
		}																			
	);

	//MACHINE_DRIVER( rthunder, 512 )
        //#define MACHINE_DRIVER(NAME,GFX)												
	static MachineDriver machine_driver_rthunder = new MachineDriver
	(																				
		new MachineCPU[] {																			
			new MachineCPU(																		
				CPU_M6809,															
				6000000/4,		/* ? */												
				/*49152000/32, rthunder doesn't work with this */					
				readmem1,writemem1,null,null,												
				namco86_interrupt1,1												
			),																		
			new MachineCPU(																		
				CPU_M6809,															
				49152000/32, 		/* ? */											
				rthunder_readmem2,rthunder_writemem2,null,null,								
				namco86_interrupt2,1												
			),																		
			new MachineCPU(																		
				CPU_HD63701,	/* or compatible 6808 with extra instructions */	
				49152000/32, 		/* ? */											
				rthunder_mcu_readmem,rthunder_mcu_writemem,mcu_readport,mcu_writeport,	
				interrupt, 1	/* ??? */											
			)																		
		},																			
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,									
		100, /* cpu slices */														
		namco86_init_machine, /* init machine */									
																					
		/* video hardware */														
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),									
		gfxdecodeinfo_512,
		512,4096,																	
		namcos86_vh_convert_color_prom,												
																					
		VIDEO_TYPE_RASTER,															
		null,																			
		namcos86_vh_start,															
		null,																			
		namcos86_vh_screenrefresh,													
																					
		/* sound hardware */														
		0,0,0,0,																	
		new MachineSound[] {																			
			new MachineSound(																		
				SOUND_YM2151,														
				ym2151_interface													
			),																		
			new MachineSound(																		
				SOUND_NAMCO,														
				namco_interface													
			),																		
			new MachineSound(																		
				SOUND_SAMPLES,														
				samples_interface													
			),																		
			new MachineSound(																		
				SOUND_CUSTOM,	/* actually initializes the samples */				
				custom_interface													
			)																		
		}																			
	);

	//MACHINE_DRIVER( wndrmomo, 512 )
        //#define MACHINE_DRIVER(NAME,GFX)												
	static MachineDriver machine_driver_wndrmomo = new MachineDriver
	(																				
		new MachineCPU[] {																			
			new MachineCPU(																		
				CPU_M6809,															
				6000000/4,		/* ? */												
				/*49152000/32, rthunder doesn't work with this */					
				readmem1,writemem1,null,null,												
				namco86_interrupt1,1												
			),																		
			new MachineCPU(																		
				CPU_M6809,															
				49152000/32, 		/* ? */											
				wndrmomo_readmem2,wndrmomo_writemem2,null,null,								
				namco86_interrupt2,1												
			),																		
			new MachineCPU(																		
				CPU_HD63701,	/* or compatible 6808 with extra instructions */	
				49152000/32, 		/* ? */											
				wndrmomo_mcu_readmem,wndrmomo_mcu_writemem,mcu_readport,mcu_writeport,	
				interrupt, 1	/* ??? */											
			)																		
		},																			
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,									
		100, /* cpu slices */														
		namco86_init_machine, /* init machine */									
																					
		/* video hardware */														
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),									
		gfxdecodeinfo_512,														
		512,4096,																	
		namcos86_vh_convert_color_prom,												
																					
		VIDEO_TYPE_RASTER,															
		null,																			
		namcos86_vh_start,															
		null,																			
		namcos86_vh_screenrefresh,													
																					
		/* sound hardware */														
		0,0,0,0,																	
		new MachineSound[] {																			
			new MachineSound(																		
				SOUND_YM2151,														
				ym2151_interface													
			),																		
			new MachineSound(																		
				SOUND_NAMCO,														
				namco_interface													
			),																		
			new MachineSound(																		
				SOUND_SAMPLES,														
				samples_interface													
			),																		
			new MachineSound(																		
				SOUND_CUSTOM,	/* actually initializes the samples */				
				custom_interface													
			)																		
		}																			
	);
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_hopmappy = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "hm1",         0x08000, 0x8000, 0x1a83914e );
		/* 9d empty */
	
		/* the CPU1 ROM expansion board is not present in this game */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "hm2",         0xc000, 0x4000, 0xc46cda65 );
		/* 12d empty */
	
		ROM_REGION( 0x06000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "hm6",         0x00000, 0x04000, 0xfd0e8887 );/* plane 1,2 */
		ROM_FILL(                0x04000, 0x02000, 0 );		/* no plane 3 */
	
		ROM_REGION( 0x06000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "hm5",         0x00000, 0x04000, 0x9c4f31ae );/* plane 1,2 */
		ROM_FILL(                0x04000, 0x02000, 0 );		/* no plane 3 */
	
		ROM_REGION( 0x40000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "hm4",         0x00000, 0x8000, 0x78719c52 );
		/* 12k/l/m/p/r/t/u empty */
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "hm11.bpr",    0x0000, 0x0200, 0xcc801088 );/* red & green components */
		ROM_LOAD( "hm12.bpr",    0x0200, 0x0200, 0xa1cb71c5 );/* blue component */
		ROM_LOAD( "hm13.bpr",    0x0400, 0x0800, 0xe362d613 );/* tiles colortable */
		ROM_LOAD( "hm14.bpr",    0x0c00, 0x0800, 0x678252b4 );/* sprites colortable */
		ROM_LOAD( "hm15.bpr",    0x1400, 0x0020, 0x475bf500 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "hm3",         0x08000, 0x2000, 0x6496e1db );
		ROM_LOAD( "pl1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		/* the PCM expansion board is not present in this game */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_skykiddx = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "sk3_1b.9c", 0x08000, 0x8000, 0x767b3514 );
		ROM_LOAD( "sk3_2.9d",  0x10000, 0x8000, 0x74b8f8e2 );
	
		/* the CPU1 ROM expansion board is not present in this game */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "sk3_3.12c", 0x8000, 0x8000, 0x6d1084c4 );
		/* 12d empty */
	
		ROM_REGION( 0x0c000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "sk3_9.7r",  0x00000, 0x08000, 0x48675b17 );/* plane 1,2 */
		ROM_LOAD( "sk3_10.7s", 0x08000, 0x04000, 0x7418465a );/* plane 3 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "sk3_7.4r",  0x00000, 0x08000, 0x4036b735 );/* plane 1,2 */
		ROM_LOAD( "sk3_8.4s",  0x08000, 0x04000, 0x044bfd21 );/* plane 3 */
	
		ROM_REGION( 0x40000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "sk3_5.12h",  0x00000, 0x8000, 0x5c7d4399 );
		ROM_LOAD( "sk3_6.12k",  0x08000, 0x8000, 0xc908a3b2 );
		/* 12l/m/p/r/t/u empty */
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "sk3-1.3r", 0x0000, 0x0200, 0x9e81dedd );/* red & green components */
		ROM_LOAD( "sk3-2.3s", 0x0200, 0x0200, 0xcbfec4dd );/* blue component */
		ROM_LOAD( "sk3-3.4v", 0x0400, 0x0800, 0x81714109 );/* tiles colortable */
		ROM_LOAD( "sk3-4.5v", 0x0c00, 0x0800, 0x1bf25acc );/* sprites colortable */
		ROM_LOAD( "sk3-5.6u", 0x1400, 0x0020, 0xe4130804 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "sk3_4.6b",    0x08000, 0x4000, 0xe6cae2d6 );
		ROM_LOAD( "rt1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		/* the PCM expansion board is not present in this game */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_skykiddo = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "sk3-1.9c",  0x08000, 0x8000, 0x5722a291 );
		ROM_LOAD( "sk3_2.9d",  0x10000, 0x8000, 0x74b8f8e2 );
	
		/* the CPU1 ROM expansion board is not present in this game */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "sk3_3.12c", 0x8000, 0x8000, 0x6d1084c4 );
		/* 12d empty */
	
		ROM_REGION( 0x0c000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "sk3_9.7r",  0x00000, 0x08000, 0x48675b17 );/* plane 1,2 */
		ROM_LOAD( "sk3_10.7s", 0x08000, 0x04000, 0x7418465a );/* plane 3 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "sk3_7.4r",  0x00000, 0x08000, 0x4036b735 );/* plane 1,2 */
		ROM_LOAD( "sk3_8.4s",  0x08000, 0x04000, 0x044bfd21 );/* plane 3 */
	
		ROM_REGION( 0x40000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "sk3_5.12h",  0x00000, 0x8000, 0x5c7d4399 );
		ROM_LOAD( "sk3_6.12k",  0x08000, 0x8000, 0xc908a3b2 );
		/* 12l/m/p/r/t/u empty */
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "sk3-1.3r", 0x0000, 0x0200, 0x9e81dedd );/* red & green components */
		ROM_LOAD( "sk3-2.3s", 0x0200, 0x0200, 0xcbfec4dd );/* blue component */
		ROM_LOAD( "sk3-3.4v", 0x0400, 0x0800, 0x81714109 );/* tiles colortable */
		ROM_LOAD( "sk3-4.5v", 0x0c00, 0x0800, 0x1bf25acc );/* sprites colortable */
		ROM_LOAD( "sk3-5.6u", 0x1400, 0x0020, 0xe4130804 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "sk3_4.6b",    0x08000, 0x4000, 0xe6cae2d6 );
		ROM_LOAD( "rt1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		/* the PCM expansion board is not present in this game */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_roishtar = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "ri1-1c.9c", 0x08000, 0x8000, 0x14acbacb );
		ROM_LOAD( "ri1-2.9d",  0x14000, 0x2000, 0xfcd58d91 );
	
		/* the CPU1 ROM expansion board is not present in this game */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "ri1-3.12c", 0x8000, 0x8000, 0xa39829f7 );
		/* 12d empty */
	
		ROM_REGION( 0x06000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "ri1-14.7r", 0x00000, 0x04000, 0xde8154b4 );/* plane 1,2 */
		ROM_LOAD( "ri1-15.7s", 0x04000, 0x02000, 0x4298822b );/* plane 3 */
	
		ROM_REGION( 0x06000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "ri1-12.4r", 0x00000, 0x04000, 0x557e54d3 );/* plane 1,2 */
		ROM_LOAD( "ri1-13.4s", 0x04000, 0x02000, 0x9ebe8e32 );/* plane 3 */
	
		ROM_REGION( 0x40000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "ri1-5.12h",  0x00000, 0x8000, 0x46b59239 );
		ROM_LOAD( "ri1-6.12k",  0x08000, 0x8000, 0x94d9ef48 );
		ROM_LOAD( "ri1-7.12l",  0x10000, 0x8000, 0xda802b59 );
		ROM_LOAD( "ri1-8.12m",  0x18000, 0x8000, 0x16b88b74 );
		ROM_LOAD( "ri1-9.12p",  0x20000, 0x8000, 0xf3de3c2a );
		ROM_LOAD( "ri1-10.12r", 0x28000, 0x8000, 0x6dacc70d );
		ROM_LOAD( "ri1-11.12t", 0x30000, 0x8000, 0xfb6bc533 );
		/* 12u empty */
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "ri1-1.3r", 0x0000, 0x0200, 0x29cd0400 );/* red & green components */
		ROM_LOAD( "ri1-2.3s", 0x0200, 0x0200, 0x02fd278d );/* blue component */
		ROM_LOAD( "ri1-3.4v", 0x0400, 0x0800, 0xcbd7e53f );/* tiles colortable */
		ROM_LOAD( "ri1-4.5v", 0x0c00, 0x0800, 0x22921617 );/* sprites colortable */
		ROM_LOAD( "ri1-5.6u", 0x1400, 0x0020, 0xe2188075 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "ri1-4.6b",    0x00000, 0x4000, 0x552172b8 );
		ROM_CONTINUE(            0x08000, 0x4000 );
		ROM_LOAD( "rt1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		/* the PCM expansion board is not present in this game */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_genpeitd = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "gt1-1b.9c", 0x08000, 0x8000, 0x75396194 );
		/* 9d empty */
	
		ROM_REGION( 0x40000, REGION_USER1, 0 );/* bank switched data for CPU1 */
		ROM_LOAD( "gt1-10b.f1",  0x00000, 0x10000, 0x5721ad0d );
		/* h1 empty */
		/* k1 empty */
		/* m1 empty */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "gt1-2.12c", 0xc000, 0x4000, 0x302f2cb6 );
		/* 12d empty */
	
		ROM_REGION( 0x18000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "gt1-7.7r", 0x00000, 0x10000, 0xea77a211 );/* plane 1,2 */
		ROM_LOAD( "gt1-6.7s", 0x10000, 0x08000, 0x1b128a2e );/* plane 3 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "gt1-5.4r", 0x00000, 0x08000, 0x44d58b06 );/* plane 1,2 */
		ROM_LOAD( "gt1-4.4s", 0x08000, 0x04000, 0xdb8d45b0 );/* plane 3 */
	
		ROM_REGION( 0x100000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "gt1-11.12h",  0x00000, 0x20000, 0x3181a5fe );
		ROM_LOAD( "gt1-12.12k",  0x20000, 0x20000, 0x76b729ab );
		ROM_LOAD( "gt1-13.12l",  0x40000, 0x20000, 0xe332a36e );
		ROM_LOAD( "gt1-14.12m",  0x60000, 0x20000, 0xe5ffaef5 );
		ROM_LOAD( "gt1-15.12p",  0x80000, 0x20000, 0x198b6878 );
		ROM_LOAD( "gt1-16.12r",  0xa0000, 0x20000, 0x801e29c7 );
		ROM_LOAD( "gt1-8.12t",   0xc0000, 0x10000, 0xad7bc770 );
		ROM_LOAD( "gt1-9.12u",   0xe0000, 0x10000, 0xd95a5fd7 );
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "gt1-1.3r", 0x0000, 0x0200, 0x2f0ddddb );/* red & green components */
		ROM_LOAD( "gt1-2.3s", 0x0200, 0x0200, 0x87d27025 );/* blue component */
		ROM_LOAD( "gt1-3.4v", 0x0400, 0x0800, 0xc178de99 );/* tiles colortable */
		ROM_LOAD( "gt1-4.5v", 0x0c00, 0x0800, 0x9f48ef17 );/* sprites colortable */
		ROM_LOAD( "gt1-5.6u", 0x1400, 0x0020, 0xe4130804 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "gt1-3.6b",    0x04000, 0x8000, 0x315cd988 );
		ROM_LOAD( "rt1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		ROM_REGION( 0x80000, REGION_SOUND1, 0 );/* PCM samples for Hitachi CPU */
		ROM_LOAD( "gt1-17.f3",  0x00000, 0x20000, 0x26181ff8 );
		ROM_LOAD( "gt1-18.h3",  0x20000, 0x20000, 0x7ef9e5ea );
		ROM_LOAD( "gt1-19.k3",  0x40000, 0x20000, 0x38e11f6c );
		/* m3 empty */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_rthunder = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "rt3-1b.9c",  0x8000, 0x8000, 0x7d252a1b );
		/* 9d empty */
	
		ROM_REGION( 0x40000, REGION_USER1, 0 );/* bank switched data for CPU1 */
		ROM_LOAD( "rt1-17.f1",  0x00000, 0x10000, 0x766af455 );
		ROM_LOAD( "rt1-18.h1",  0x10000, 0x10000, 0x3f9f2f5d );
		ROM_LOAD( "rt1-19.k1",  0x20000, 0x10000, 0xc16675e9 );
		ROM_LOAD( "rt1-20.m1",  0x30000, 0x10000, 0xc470681b );
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "rt3-2b.12c", 0x08000, 0x8000, 0xa7ea46ee );
		ROM_LOAD( "rt3-3.12d",  0x10000, 0x8000, 0xa13f601c );
	
		ROM_REGION( 0x18000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "rt1-7.7r",  0x00000, 0x10000, 0xa85efa39 );/* plane 1,2 */
		ROM_LOAD( "rt1-8.7s",  0x10000, 0x08000, 0xf7a95820 );/* plane 3 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "rt1-5.4r",  0x00000, 0x08000, 0xd0fc470b );/* plane 1,2 */
		ROM_LOAD( "rt1-6.4s",  0x08000, 0x04000, 0x6b57edb2 );/* plane 3 */
	
		ROM_REGION( 0x80000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "rt1-9.12h",  0x00000, 0x10000, 0x8e070561 );
		ROM_LOAD( "rt1-10.12k", 0x10000, 0x10000, 0xcb8fb607 );
		ROM_LOAD( "rt1-11.12l", 0x20000, 0x10000, 0x2bdf5ed9 );
		ROM_LOAD( "rt1-12.12m", 0x30000, 0x10000, 0xe6c6c7dc );
		ROM_LOAD( "rt1-13.12p", 0x40000, 0x10000, 0x489686d7 );
		ROM_LOAD( "rt1-14.12r", 0x50000, 0x10000, 0x689e56a8 );
		ROM_LOAD( "rt1-15.12t", 0x60000, 0x10000, 0x1d8bf2ca );
		ROM_LOAD( "rt1-16.12u", 0x70000, 0x10000, 0x1bbcf37b );
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "mb7124e.3r", 0x0000, 0x0200, 0x8ef3bb9d );/* red & green components */
		ROM_LOAD( "mb7116e.3s", 0x0200, 0x0200, 0x6510a8f2 );/* blue component */
		ROM_LOAD( "mb7138h.4v", 0x0400, 0x0800, 0x95c7d944 );/* tiles colortable */
		ROM_LOAD( "mb7138h.6v", 0x0c00, 0x0800, 0x1391fec9 );/* sprites colortable */
		ROM_LOAD( "mb7112e.6u", 0x1400, 0x0020, 0xe4130804 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "rt1-4.6b",    0x04000, 0x8000, 0x00cf293f );
		ROM_LOAD( "rt1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		ROM_REGION( 0x40000, REGION_SOUND1, 0 );/* PCM samples for Hitachi CPU */
		ROM_LOAD( "rt1-21.f3",  0x00000, 0x10000, 0x454968f3 );
		ROM_LOAD( "rt1-22.h3",  0x10000, 0x10000, 0xfe963e72 );
		/* k3 empty */
		/* m3 empty */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_rthundro = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "r1",         0x8000, 0x8000, 0x6f8c1252 );
		/* 9d empty */
	
		ROM_REGION( 0x40000, REGION_USER1, 0 );/* bank switched data for CPU1 */
		ROM_LOAD( "rt1-17.f1",  0x00000, 0x10000, 0x766af455 );
		ROM_LOAD( "rt1-18.h1",  0x10000, 0x10000, 0x3f9f2f5d );
		ROM_LOAD( "r19",        0x20000, 0x10000, 0xfe9343b0 );
		ROM_LOAD( "r20",        0x30000, 0x10000, 0xf8518d4f );
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "r2",        0x08000, 0x8000, 0xf22a03d8 );
		ROM_LOAD( "r3",        0x10000, 0x8000, 0xaaa82885 );
	
		ROM_REGION( 0x18000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "rt1-7.7r",  0x00000, 0x10000, 0xa85efa39 );/* plane 1,2 */
		ROM_LOAD( "rt1-8.7s",  0x10000, 0x08000, 0xf7a95820 );/* plane 3 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "rt1-5.4r",  0x00000, 0x08000, 0xd0fc470b );/* plane 1,2 */
		ROM_LOAD( "rt1-6.4s",  0x08000, 0x04000, 0x6b57edb2 );/* plane 3 */
	
		ROM_REGION( 0x80000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "rt1-9.12h",  0x00000, 0x10000, 0x8e070561 );
		ROM_LOAD( "rt1-10.12k", 0x10000, 0x10000, 0xcb8fb607 );
		ROM_LOAD( "rt1-11.12l", 0x20000, 0x10000, 0x2bdf5ed9 );
		ROM_LOAD( "rt1-12.12m", 0x30000, 0x10000, 0xe6c6c7dc );
		ROM_LOAD( "rt1-13.12p", 0x40000, 0x10000, 0x489686d7 );
		ROM_LOAD( "rt1-14.12r", 0x50000, 0x10000, 0x689e56a8 );
		ROM_LOAD( "rt1-15.12t", 0x60000, 0x10000, 0x1d8bf2ca );
		ROM_LOAD( "rt1-16.12u", 0x70000, 0x10000, 0x1bbcf37b );
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "mb7124e.3r", 0x0000, 0x0200, 0x8ef3bb9d );/* red & green components */
		ROM_LOAD( "mb7116e.3s", 0x0200, 0x0200, 0x6510a8f2 );/* blue component */
		ROM_LOAD( "mb7138h.4v", 0x0400, 0x0800, 0x95c7d944 );/* tiles colortable */
		ROM_LOAD( "mb7138h.6v", 0x0c00, 0x0800, 0x1391fec9 );/* sprites colortable */
		ROM_LOAD( "mb7112e.6u", 0x1400, 0x0020, 0xe4130804 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "r4",          0x04000, 0x8000, 0x0387464f );
		ROM_LOAD( "rt1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		ROM_REGION( 0x40000, REGION_SOUND1, 0 );/* PCM samples for Hitachi CPU */
		ROM_LOAD( "rt1-21.f3",  0x00000, 0x10000, 0x454968f3 );
		ROM_LOAD( "rt1-22.h3",  0x10000, 0x10000, 0xfe963e72 );
		/* k3 empty */
		/* m3 empty */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_wndrmomo = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x18000, REGION_CPU1, 0 );
		ROM_LOAD( "wm1-1.9c", 0x8000, 0x8000, 0x34b50bf0 );
		/* 9d empty */
	
		ROM_REGION( 0x40000, REGION_USER1, 0 );/* bank switched data for CPU1 */
		ROM_LOAD( "wm1-16.f1", 0x00000, 0x10000, 0xe565f8f3 );
		/* h1 empty */
		/* k1 empty */
		/* m1 empty */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 );
		ROM_LOAD( "wm1-2.12c", 0x8000, 0x8000, 0x3181efd0 );
		/* 12d empty */
	
		ROM_REGION( 0x0c000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "wm1-6.7r", 0x00000, 0x08000, 0x93955fbb );/* plane 1,2 */
		ROM_LOAD( "wm1-7.7s", 0x08000, 0x04000, 0x7d662527 );/* plane 3 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "wm1-4.4r", 0x00000, 0x08000, 0xbbe67836 );/* plane 1,2 */
		ROM_LOAD( "wm1-5.4s", 0x08000, 0x04000, 0xa81b481f );/* plane 3 */
	
		ROM_REGION( 0x80000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "wm1-8.12h",  0x00000, 0x10000, 0x14f52e72 );
		ROM_LOAD( "wm1-9.12k",  0x10000, 0x10000, 0x16f8cdae );
		ROM_LOAD( "wm1-10.12l", 0x20000, 0x10000, 0xbfbc1896 );
		ROM_LOAD( "wm1-11.12m", 0x30000, 0x10000, 0xd775ddb2 );
		ROM_LOAD( "wm1-12.12p", 0x40000, 0x10000, 0xde64c12f );
		ROM_LOAD( "wm1-13.12r", 0x50000, 0x10000, 0xcfe589ad );
		ROM_LOAD( "wm1-14.12t", 0x60000, 0x10000, 0x2ae21a53 );
		ROM_LOAD( "wm1-15.12u", 0x70000, 0x10000, 0xb5c98be0 );
	
		ROM_REGION( 0x1420, REGION_PROMS, 0 );
		ROM_LOAD( "wm1-1.3r", 0x0000, 0x0200, 0x1af8ade8 );/* red & green components */
		ROM_LOAD( "wm1-2.3s", 0x0200, 0x0200, 0x8694e213 );/* blue component */
		ROM_LOAD( "wm1-3.4v", 0x0400, 0x0800, 0x2ffaf9a4 );/* tiles colortable */
		ROM_LOAD( "wm1-4.5v", 0x0c00, 0x0800, 0xf4e83e0b );/* sprites colortable */
		ROM_LOAD( "wm1-5.6u", 0x1400, 0x0020, 0xe4130804 );/* tile address decoder (used at runtime) */
	
		ROM_REGION( 0x10000, REGION_CPU3, 0 );
		ROM_LOAD( "wm1-3.6b",    0x04000, 0x8000, 0x55f01df7 );
		ROM_LOAD( "rt1-mcu.bin", 0x0f000, 0x1000, 0x6ef08fb3 );
	
		ROM_REGION( 0x40000, REGION_SOUND1, 0 );/* PCM samples for Hitachi CPU */
		ROM_LOAD( "wm1-17.f3", 0x00000, 0x10000, 0xbea3c318 );
		ROM_LOAD( "wm1-18.h3", 0x10000, 0x10000, 0x6d73bcc5 );
		ROM_LOAD( "wm1-19.k3", 0x20000, 0x10000, 0xd288e912 );
		ROM_LOAD( "wm1-20.m3", 0x30000, 0x10000, 0x076a72cb );
	ROM_END(); }}; 
	
	
	
	public static InitDriverPtr init_namco86 = new InitDriverPtr() { public void handler()
	{
		int size;
		UBytePtr gfx = new UBytePtr();
		UBytePtr buffer = new UBytePtr();
	
		/* shuffle tile ROMs so regular gfx unpack routines can be used */
		gfx = new UBytePtr(memory_region(REGION_GFX1));
		size = memory_region_length(REGION_GFX1) * 2 / 3;
		buffer = new UBytePtr( size );
	
		if ( buffer != null)
		{
			UBytePtr dest1 = new UBytePtr(gfx);
			UBytePtr dest2 = new UBytePtr(gfx, ( size / 2 ));
			UBytePtr mono = new UBytePtr(gfx, size);
			int i;
	
			memcpy( buffer, gfx, size );
	
			for ( i = 0; i < size; i += 2 )
			{
				int data1 = buffer.read(i);
				int data2 = buffer.read(i+1);
				dest1.writeinc( ( data1 << 4 ) | ( data2 & 0xf ) );
				dest2.writeinc( ( data1 & 0xf0 ) | ( data2 >> 4 ) );
	
				mono.write( mono.read() ^ 0xff); 
                                mono.inc();
			}
	
			buffer = null;
		}
	
		gfx = memory_region(REGION_GFX2);
		size = memory_region_length(REGION_GFX2) * 2 / 3;
		buffer = new UBytePtr( size );
	
		if ( buffer != null )
		{
			UBytePtr dest1 = new UBytePtr(gfx);
			UBytePtr dest2 = new UBytePtr(gfx, ( size / 2 ));
			UBytePtr mono = new UBytePtr(gfx, size);
			int i;
	
			memcpy( buffer, gfx, size );
	
			for ( i = 0; i < size; i += 2 )
			{
				int data1 = buffer.read(i);
				int data2 = buffer.read(i+1);
				dest1.writeinc( ( data1 << 4 ) | ( data2 & 0xf ) );
				dest2.writeinc( ( data1 & 0xf0 ) | ( data2 >> 4 ) );
	
				mono.write( mono.read() ^ 0xff ); 
                                mono.inc();
			}
	
			buffer = null;
		}
	} };
	
	
	
	public static WriteHandlerPtr roishtar_semaphore_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    rthunder_videoram1_w.handler(0x7e24-0x6000+offset,data);
	
	    if (data == 0x02)
		    cpu_spinuntil_int();
	} };
	
	public static InitDriverPtr init_roishtar = new InitDriverPtr() { public void handler()
	{
		/* install hook to avoid hang at game over */
	    install_mem_write_handler(1, 0x7e24, 0x7e24, roishtar_semaphore_w);
	
		init_namco86.handler();
	} };
	
	
	
	public static GameDriver driver_hopmappy	   = new GameDriver("1986"	,"hopmappy"	,"namcos86.java"	,rom_hopmappy,null	,machine_driver_hopmappy	,input_ports_hopmappy	,init_namco86	,ROT0	,	"Namco", "Hopping Mappy" );
	public static GameDriver driver_skykiddx	   = new GameDriver("1986"	,"skykiddx"	,"namcos86.java"	,rom_skykiddx,null	,machine_driver_skykiddx	,input_ports_skykiddx	,init_namco86	,ROT180	,	"Namco", "Sky Kid Deluxe (set 1)" );
	public static GameDriver driver_skykiddo	   = new GameDriver("1986"	,"skykiddo"	,"namcos86.java"	,rom_skykiddo,driver_skykiddx	,machine_driver_skykiddx	,input_ports_skykiddx	,init_namco86	,ROT180	,	"Namco", "Sky Kid Deluxe (set 2)" );
	public static GameDriver driver_roishtar	   = new GameDriver("1986"	,"roishtar"	,"namcos86.java"	,rom_roishtar,null	,machine_driver_roishtar	,input_ports_roishtar	,init_roishtar	,ROT0	,	"Namco", "The Return of Ishtar" );
	public static GameDriver driver_genpeitd	   = new GameDriver("1986"	,"genpeitd"	,"namcos86.java"	,rom_genpeitd,null	,machine_driver_genpeitd	,input_ports_genpeitd	,init_namco86	,ROT0	,	"Namco", "Genpei ToumaDen" );
	public static GameDriver driver_rthunder	   = new GameDriver("1986"	,"rthunder"	,"namcos86.java"	,rom_rthunder,null	,machine_driver_rthunder	,input_ports_rthunder	,init_namco86	,ROT0	,	"Namco", "Rolling Thunder (new version)" );
	public static GameDriver driver_rthundro	   = new GameDriver("1986"	,"rthundro"	,"namcos86.java"	,rom_rthundro,driver_rthunder	,machine_driver_rthunder	,input_ports_rthundro	,init_namco86	,ROT0	,	"Namco", "Rolling Thunder (old version)" );
	public static GameDriver driver_wndrmomo	   = new GameDriver("1987"	,"wndrmomo"	,"namcos86.java"	,rom_wndrmomo,null	,machine_driver_wndrmomo	,input_ports_wndrmomo	,init_namco86	,ROT0	,	"Namco", "Wonder Momo" );
}
