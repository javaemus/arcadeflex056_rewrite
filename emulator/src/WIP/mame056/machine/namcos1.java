/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.machine;

import static WIP.mame056.vidhrdw.namcos1.*;
import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.inptport.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.timerH.*;
import static WIP2.mame056.timer.*;


public class namcos1
{
	
	public static int NEW_TIMER = 0; /* CPU slice optimize with new timer system */
	
	public static int NAMCOS1_MAX_BANK = 0x400;
	
	/* from vidhrdw */

        public static int NAMCOS1_MAX_KEY = 0x100;
        public static int[] key = new int[NAMCOS1_MAX_KEY];

        static UBytePtr s1ram = new UBytePtr();

        static int namcos1_cpu1_banklatch;
        public static int namcos1_reset = 0;

        static int berabohm_input_counter;

   	
	/*******************************************************************************
	*																			   *
	*	BANK area handling															*
	*																			   *
	*******************************************************************************/
	
	/* Bank handler definitions */
	public static class bankhandler {
		public ReadHandlerPtr bank_handler_r;
		public WriteHandlerPtr bank_handler_w;
		public int 		  bank_offset;
		public UBytePtr bank_pointer = new UBytePtr();
	};
	
	/* hardware elements of 1Mbytes physical memory space */
	static bankhandler[] namcos1_bank_element = new bankhandler[NAMCOS1_MAX_BANK];
        static {
            for (int i=0 ; i<NAMCOS1_MAX_BANK ; i++)
                namcos1_bank_element[i] = new bankhandler();
        }
	
	static int org_bank_handler_r[] =
	{
		MRA_BANK1 ,MRA_BANK2 ,MRA_BANK3 ,MRA_BANK4 ,
		MRA_BANK5 ,MRA_BANK6 ,MRA_BANK7 ,MRA_BANK8 ,
		MRA_BANK9 ,MRA_BANK10,MRA_BANK11,MRA_BANK12,
		MRA_BANK13,MRA_BANK14,MRA_BANK15,MRA_BANK16
	};
	
	static int org_bank_handler_w[] =
	{
		MWA_BANK1 ,MWA_BANK2 ,MWA_BANK3 ,MWA_BANK4 ,
		MWA_BANK5 ,MWA_BANK6 ,MWA_BANK7 ,MWA_BANK8 ,
		MWA_BANK9 ,MWA_BANK10,MWA_BANK11,MWA_BANK12,
		MWA_BANK13,MWA_BANK14,MWA_BANK15,MWA_BANK16
	};
	
	
	
	/*******************************************************************************
	*																			   *
	*	Key emulation (CUS136) Rev1 (Pacmania & Galaga 88)						   *
	*																			   *
	*******************************************************************************/
	
	static int key_id;
	static int key_id_query;
	
	public static ReadHandlerPtr rev1_key_r  = new ReadHandlerPtr() { public int handler(int offset) {
	//	logerror("CPU #%d PC %08x: keychip read %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,key[offset]);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip read %04x\n",cpu_getactivecpu(),cpu_get_pc(),offset);
			return 0;
		}
		return key[offset];
	} };
	
	public static WriteHandlerPtr rev1_key_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		int divider=0, divide_32 = 0;
		//logerror("CPU #%d PC %08x: keychip write %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip write %04x=%04x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
			return;
		}
	
		key[offset] = data;
	
		switch ( offset )
		{
		case 0x01:
			divider = ( key[0] << 8 ) + key[1];
			break;
		case 0x03:
			{
				int d=0;
				int	v1, v2;
				int	l=0;
	
				if ( divide_32 != 0 )
					l = d << 16;
	
				d = ( key[2] << 8 ) + key[3];
	
				if ( divider == 0 ) {
					v1 = 0xffff;
					v2 = 0;
				} else {
					if ( divide_32 != 0 ) {
						l |= d;
	
						v1 = l / divider;
						v2 = l % divider;
					} else {
						v1 = d / divider;
						v2 = d % divider;
					}
				}
	
				key[2] = v1 >> 8;
				key[3] = v1;
				key[0] = v2 >> 8;
				key[1] = v2;
			}
			break;
		case 0x04:
			if ( key[4] == key_id_query ) /* get key number */
				key[4] = key_id;
	
			if ( key[4] == 0x0c )
				divide_32 = 1;
			else
				divide_32 = 0;
			break;
		}
	} };
	
	/*******************************************************************************
	*																			   *
	*	Key emulation (CUS136) Rev2 (Dragon Spirit, Blazer, World Court)		   *
	*																			   *
	*******************************************************************************/
	
	public static ReadHandlerPtr rev2_key_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		//logerror("CPU #%d PC %08x: keychip read %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,key[offset]);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip read %04x\n",cpu_getactivecpu(),cpu_get_pc(),offset);
			return 0;
		}
		return key[offset];
	} };
	
	public static WriteHandlerPtr rev2_key_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		//logerror("CPU #%d PC %08x: keychip write %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip write %04x=%04x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
			return;
		}
		key[offset] = data;
	
		switch(offset)
		{
		case 0x00:
			if ( data == 1 )
			{
				/* fetch key ID */
				key[3] = key_id;
				return;
			}
			break;
		case 0x02:
			/* $f2 = Dragon Spirit, $b7 = Blazer , $35($d9) = worldcourt */
			if ( key[3] == 0xf2 || key[3] == 0xb7 || key[3] == 0x35 )
			{
				switch( key[0] )
				{
					case 0x10: key[0] = 0x05; key[1] = 0x00; key[2] = 0xc6; break;
					case 0x12: key[0] = 0x09; key[1] = 0x00; key[2] = 0x96; break;
					case 0x15: key[0] = 0x0a; key[1] = 0x00; key[2] = 0x8f; break;
					case 0x22: key[0] = 0x14; key[1] = 0x00; key[2] = 0x39; break;
					case 0x32: key[0] = 0x31; key[1] = 0x00; key[2] = 0x12; break;
					case 0x3d: key[0] = 0x35; key[1] = 0x00; key[2] = 0x27; break;
					case 0x54: key[0] = 0x10; key[1] = 0x00; key[2] = 0x03; break;
					case 0x58: key[0] = 0x49; key[1] = 0x00; key[2] = 0x23; break;
					case 0x7b: key[0] = 0x48; key[1] = 0x00; key[2] = 0xd4; break;
					case 0xc7: key[0] = 0xbf; key[1] = 0x00; key[2] = 0xe8; break;
				}
				return;
			}
			break;
		case 0x03:
			/* $c2 = Dragon Spirit, $b6 = Blazer */
			if ( key[3] == 0xc2 || key[3] == 0xb6 ) {
				key[3] = 0x36;
				return;
			}
			/* $d9 = World court */
			if ( key[3] == 0xd9 )
			{
				key[3] = 0x35;
				return;
			}
			break;
		case 0x3f:	/* Splatter House */
			key[0x3f] = 0xb5;
			key[0x36] = 0xb5;
			return;
		}
		/* ?? */
		if ( key[3] == 0x01 ) {
			if ( key[0] == 0x40 && key[1] == 0x04 && key[2] == 0x00 ) {
				key[1] = 0x00; key[2] = 0x10;
				return;
			}
		}
	} };
	
	/*******************************************************************************
	*																			   *
	*	Key emulation (CUS136) for Dangerous Seed								   *
	*																			   *
	*******************************************************************************/
	
	public static ReadHandlerPtr dangseed_key_r  = new ReadHandlerPtr() { public int handler(int offset) {
	//	logerror("CPU #%d PC %08x: keychip read %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,key[offset]);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip read %04x\n",cpu_getactivecpu(),cpu_get_pc(),offset);
			return 0;
		}
		return key[offset];
	} };
	
	public static WriteHandlerPtr dangseed_key_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		int i;
	//	logerror("CPU #%d PC %08x: keychip write %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip write %04x=%04x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
			return;
		}
	
		key[offset] = data;
	
		switch ( offset )
		{
			case 0x50:
				for ( i = 0; i < 0x50; i++ ) {
					key[i] = ( data >> ( ( i >> 4 ) & 0x0f ) ) & 0x0f;
					key[i] |= ( i & 0x0f ) << 4;
				}
				break;
	
			case 0x57:
				key[3] = key_id;
				break;
		}
	} };
	
	/*******************************************************************************
	*																			   *
	*	Key emulation (CUS136) for Dragon Spirit								   *
	*																			   *
	*******************************************************************************/
	
	public static ReadHandlerPtr dspirit_key_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		//logerror("CPU #%d PC %08x: keychip read %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,key[offset]);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip read %04x\n",cpu_getactivecpu(),cpu_get_pc(),offset);
			return 0;
		}
		return key[offset];
	} };
	
	public static WriteHandlerPtr dspirit_key_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int divisor=0;
	//	logerror("CPU #%d PC %08x: keychip write %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip write %04x=%04x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
			return;
		}
		key[offset] = data;
	
		switch(offset)
		{
		case 0x00:
			if ( data == 1 )
			{
				/* fetch key ID */
				key[3] = key_id;
			} else
				divisor = data;
			break;
	
		case 0x01:
			if ( key[3] == 0x01 ) { /* division gets resolved on latch to $1 */
				int d, v1, v2;
	
				d = ( key[1] << 8 ) + key[2];
	
				if ( divisor == 0 ) {
					v1 = 0xffff;
					v2 = 0;
				} else {
					v1 = d / divisor;
					v2 = d % divisor;
				}
	
				key[0] = v2 & 0xff;
				key[1] = v1 >> 8;
				key[2] = v1 & 0xff;
	
				return;
			}
	
			if ( key[3] != 0xf2 ) { /* if its an invalid mode, clear regs */
				key[0] = 0;
				key[1] = 0;
				key[2] = 0;
			}
			break;
		case 0x02:
			if ( key[3] == 0xf2 ) { /* division gets resolved on latch to $2 */
				int d, v1, v2;
	
				d = ( key[1] << 8 ) + key[2];
	
				if ( divisor == 0 ) {
					v1 = 0xffff;
					v2 = 0;
				} else {
					v1 = d / divisor;
					v2 = d % divisor;
				}
	
				key[0] = v2 & 0xff;
				key[1] = v1 >> 8;
				key[2] = v1 & 0xff;
	
				return;
			}
	
			if ( key[3] != 0x01 ) { /* if its an invalid mode, clear regs */
				key[0] = 0;
				key[1] = 0;
				key[2] = 0;
			}
			break;
		case 0x03:
			if ( key[3] != 0xf2 && key[3] != 0x01 ) /* if the mode is unknown return the id on $3 */
				key[3] = key_id;
			break;
		}
	} };
	
	/*******************************************************************************
	*																			   *
	*	Key emulation (CUS136) for Blazer										   *
	*																			   *
	*******************************************************************************/
	
	public static ReadHandlerPtr blazer_key_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		logerror("CPU #%d PC %08x: keychip read %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,key[offset]);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip read %04x\n",cpu_getactivecpu(),cpu_get_pc(),offset);
			return 0;
		}
		return key[offset];
	} };
	
	public static WriteHandlerPtr blazer_key_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int divisor=0;
		logerror("CPU #%d PC %08x: keychip write %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip write %04x=%04x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
			return;
		}
		key[offset] = data;
	
		switch(offset)
		{
		case 0x00:
			if ( data == 1 )
			{
				/* fetch key ID */
				key[3] = key_id;
			} else
				divisor = data;
			break;
	
		case 0x01:
			if ( key[3] != 0xb7 ) { /* if its an invalid mode, clear regs */
				key[0] = 0;
				key[1] = 0;
				key[2] = 0;
			}
			break;
	
		case 0x02:
			if ( key[3] == 0xb7 ) { /* division gets resolved on latch to $2 */
				int d, v1, v2;
	
				d = ( key[1] << 8 ) + key[2];
	
				if ( divisor == 0 ) {
					v1 = 0xffff;
					v2 = 0;
				} else {
					v1 = d / divisor;
					v2 = d % divisor;
				}
	
				key[0] = v2 & 0xff;
				key[1] = v1 >> 8;
				key[2] = v1 & 0xff;
	
				return;
			}
	
			/* if its an invalid mode, clear regs */
			key[0] = 0;
			key[1] = 0;
			key[2] = 0;
			break;
		case 0x03:
			if ( key[3] != 0xb7 ) { /* if the mode is unknown return the id on $3 */
				key[3] = key_id;
			}
			break;
		}
	} };
	
	/*******************************************************************************
	*																			   *
	*	Key emulation (CUS136) for World Stadium								   *
	*																			   *
	*******************************************************************************/
	
	public static ReadHandlerPtr ws_key_r  = new ReadHandlerPtr() { public int handler(int offset) {
	//	logerror("CPU #%d PC %08x: keychip read %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,key[offset]);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip read %04x\n",cpu_getactivecpu(),cpu_get_pc(),offset);
			return 0;
		}
		return key[offset];
	} };
	
	public static WriteHandlerPtr ws_key_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		int divider=0;
		//logerror("CPU #%d PC %08x: keychip write %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
		if(offset >= NAMCOS1_MAX_KEY)
		{
			logerror("CPU #%d PC %08x: unmapped keychip write %04x=%04x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
			return;
		}
	
		key[offset] = data;
	
		switch ( offset )
		{
		case 0x01:
			divider = ( key[0] << 8 ) + key[1];
			break;
		case 0x03:
			{
				int d;
				int v1, v2;
	
				d = ( key[2] << 8 ) + key[3];
	
				if ( divider == 0 ) {
					v1 = 0xffff;
					v2 = 0;
				} else {
					v1 = d / divider;
					v2 = d % divider;
				}
	
				key[2] = v1 >> 8;
				key[3] = v1;
				key[0] = v2 >> 8;
				key[1] = v2;
			}
			break;
		case 0x04:
			key[4] = key_id;
			break;
		}
	} };
	
	/*******************************************************************************
	*																			   *
	*	Key emulation (CUS181) for SplatterHouse								   *
	*																			   *
	*******************************************************************************/
	
	public static ReadHandlerPtr splatter_key_r  = new ReadHandlerPtr() { public int handler(int offset) {
	//	logerror("CPU #%d PC %08x: keychip read %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,key[offset]);
		switch( ( offset >> 4 ) & 0x07 ) {
			case 0x00:
			case 0x06:
				return 0xff;
				//break;
	
			case 0x01:
			case 0x02:
			case 0x05:
			case 0x07:
				return ( ( offset & 0x0f ) << 4 ) | 0x0f;
				//break;
	
			case 0x03:
				return 0xb5;
				//break;
	
			case 0x04:
				{
					int data = 0x29;
	
					if ( offset >= 0x1000 )
						data |= 0x80;
					if ( offset >= 0x2000 )
						data |= 0x04;
	
					return data;
				}
				//break;
		}
	
		/* make compiler happy */
		return 0;
	} };
	
	public static WriteHandlerPtr splatter_key_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
	//	logerror("CPU #%d PC %08x: keychip write %04X=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
		/* ignored */
	} };
	
	
	/*******************************************************************************
	*																			   *
	*	Banking emulation (CUS117)												   *
	*																			   *
	*******************************************************************************/
	
	public static ReadHandlerPtr soundram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		if(offset<0x100)
			return namcos1_wavedata_r.handler(offset);
		if(offset<0x140)
			return namcos1_sound_r.handler(offset-0x100);
	
		/* shared ram */
		return namco_wavedata.read(offset);
	} };
	
	public static WriteHandlerPtr soundram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if(offset<0x100)
		{
			namcos1_wavedata_w.handler(offset,data);
			return;
		}
		if(offset<0x140)
		{
			namcos1_sound_w.handler(offset-0x100,data);
			return;
		}
		/* shared ram */
		namco_wavedata.write(offset, data);
	
		//if(offset>=0x1000)
		//	logerror("CPU #%d PC %04x: write shared ram %04x=%02x\n",cpu_getactivecpu(),cpu_get_pc(),offset,data);
	} };
	
	/* ROM handlers */
	
	public static WriteHandlerPtr rom_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		logerror("CPU #%d PC %04x: warning - write %02x to rom address %04x\n",cpu_getactivecpu(),cpu_get_pc(),data,offset);
	} };
	
	/* error handlers */
	public static ReadHandlerPtr unknown_r  = new ReadHandlerPtr() { public int handler(int offset) {
		logerror("CPU #%d PC %04x: warning - read from unknown chip\n",cpu_getactivecpu(),cpu_get_pc() );
		return 0;
	} };
	
	public static WriteHandlerPtr unknown_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		logerror("CPU #%d PC %04x: warning - wrote to unknown chip\n",cpu_getactivecpu(),cpu_get_pc() );
	} };
	
	/* Main bankswitching routine */
	public static void namcos1_bankswitch(int cpu, int offset, int data)
	{
		int chip = 0;
		ReadHandlerPtr handler_r;
		WriteHandlerPtr handler_w;
		int offs;
	
		if (( offset & 1 ) != 0) {
			int bank = (cpu*8) + ( ( offset >> 9 ) & 0x07 );
			chip &= 0x0300;
			chip |= ( data & 0xff );
	
			/* for BANK handlers , memory direct and OP-code base */
			cpu_setbank(bank+1,namcos1_bank_element[chip].bank_pointer);
	
			/* Addition OFFSET for stub handlers */
			offs = namcos1_bank_element[chip].bank_offset;
	
			/* read hardware */
			handler_r = namcos1_bank_element[chip].bank_handler_r;
			if( handler_r != null ){
				/* I/O handler */
				memory_set_bankhandler_r( bank+1,offs,handler_r);
                        } else {	/* memory direct */
				memory_set_bankhandler_r( bank+1,0,org_bank_handler_r[bank] );
                        }
	
			/* write hardware */
			handler_w = namcos1_bank_element[chip].bank_handler_w;
			if( handler_w != null){
				/* I/O handler */
				memory_set_bankhandler_w( bank+1,offs,handler_w);
                        } else {	/* memory direct */
				memory_set_bankhandler_w( bank+1,0,org_bank_handler_w[bank] );
                        }
	
			/* unmapped bank warning */
			if( handler_r == unknown_r)
			{
				logerror("CPU #%d PC %04x:warning unknown chip selected bank %x=$%04x\n", cpu , cpu_get_pc(), bank , chip );
			}
	
			/* renew pc base */
	//		change_pc16(cpu_get_pc());
		} else {
			chip &= 0x00ff;
			chip |= ( data & 0xff ) << 8;
		}
	}
	
	public static WriteHandlerPtr namcos1_bankswitch_w = new WriteHandlerPtr() {public void handler(int offset, int data) {
		namcos1_bankswitch(cpu_getactivecpu(), offset, data);
	} };

	/* Sub cpu set start bank port */
	public static WriteHandlerPtr namcos1_subcpu_bank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		//logerror("cpu1 bank selected %02x=%02x\n",offset,data);
		namcos1_cpu1_banklatch = (namcos1_cpu1_banklatch&0x300)|data;
		/* Prepare code for Cpu 1 */
		namcos1_bankswitch( 1, 0x0e00, namcos1_cpu1_banklatch>>8  );
		namcos1_bankswitch( 1, 0x0e01, namcos1_cpu1_banklatch&0xff);
		/* cpu_set_reset_line(1,PULSE_LINE); */
	
	} };
	
	/*******************************************************************************
	*																			   *
	*	63701 MCU emulation (CUS64) 											   *
	*																			   *
	*******************************************************************************/
	
	static int mcu_patch_data;
	
	public static WriteHandlerPtr namcos1_cpu_control_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	//	logerror("reset control pc=%04x %02x\n",cpu_get_pc(),data);
		if(( (data&1)^namcos1_reset) != 0)
		{
			namcos1_reset = data&1;
			if (namcos1_reset != 0)
			{
				cpu_set_reset_line(1,CLEAR_LINE);
				cpu_set_reset_line(2,CLEAR_LINE);
				cpu_set_reset_line(3,CLEAR_LINE);
				mcu_patch_data = 0;
			}
			else
			{
				cpu_set_reset_line(1,ASSERT_LINE);
				cpu_set_reset_line(2,ASSERT_LINE);
				cpu_set_reset_line(3,ASSERT_LINE);
			}
		}
	} };
	
	/*******************************************************************************
	*																			   *
	*	Sound banking emulation (CUS121)										   *
	*																			   *
	*******************************************************************************/
	
	public static WriteHandlerPtr namcos1_sound_bankswitch_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU3));
		int bank = ( data >> 4 ) & 0x07;
	
		cpu_setbank( 17, new UBytePtr(RAM, 0x0c000 + ( 0x4000 * bank ) ) );
	} };
	
	/*******************************************************************************
	*																			   *
	*	CPU idling spinlock routine 											   *
	*																			   *
	*******************************************************************************/
	static UBytePtr sound_spinlock_ram = new UBytePtr();
	static int sound_spinlock_pc;
	
	/* sound cpu */
	public static ReadHandlerPtr namcos1_sound_spinlock_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		if(cpu_get_pc()==sound_spinlock_pc && sound_spinlock_ram.read() == 0)
			cpu_spinuntil_int();
		return sound_spinlock_ram.read();
	} };
	
	/*******************************************************************************
	*																			   *
	*	MCU banking emulation and patch 										   *
	*																			   *
	*******************************************************************************/
	
	/* mcu banked rom area select */
	public static WriteHandlerPtr namcos1_mcu_bankswitch_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int addr;
		/* bit 2-7 : chip select line of ROM chip */
		switch(data&0xfc)
		{
		case 0xf8: addr = 0x10000; break; /* bit 2 : ROM 0 */
		case 0xf4: addr = 0x30000; break; /* bit 3 : ROM 1 */
		case 0xec: addr = 0x50000; break; /* bit 4 : ROM 2 */
		case 0xdc: addr = 0x70000; break; /* bit 5 : ROM 3 */
		case 0xbc: addr = 0x90000; break; /* bit 6 : ROM 4 */
		case 0x7c: addr = 0xb0000; break; /* bit 7 : ROM 5 */
		default:   addr = 0x100000; /* illegal */
		}
		/* bit 0-1 : address line A15-A16 */
		addr += (data&3)*0x8000;
		if( addr >= memory_region_length(REGION_CPU4))
		{
			logerror("unmapped mcu bank selected pc=%04x bank=%02x\n",cpu_get_pc(),data);
			addr = 0x4000;
		}
		cpu_setbank( 20, new UBytePtr(memory_region(REGION_CPU4), addr) );
	} };
	
	/* This point is very obscure, but i havent found any better way yet. */
	/* Works with all games so far. 									  */
	
	/* patch points of memory address */
	/* CPU0/1 bank[17f][1000] */
	/* CPU2   [7000]	  */
	/* CPU3   [c000]	  */
	
	/* This memory point should be set $A6 by anywhere, but 		*/
	/* I found set $A6 only initialize in MCU						*/
	/* This patch kill write this data by MCU case $A6 to xx(clear) */
	
	public static WriteHandlerPtr namcos1_mcu_patch_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		//logerror("mcu C000 write pc=%04x data=%02x\n",cpu_get_pc(),data);
		if(mcu_patch_data == 0xa6) return;
		mcu_patch_data = data;
		cpu_bankbase[19].offset = data;
	} };
	
	/*******************************************************************************
	*																			   *
	*	Initialization															   *
	*																			   *
	*******************************************************************************/
	
	static void namcos1_install_bank(int start,int end,ReadHandlerPtr hr,WriteHandlerPtr hw,
				  int offset,UBytePtr pointer)
	{
		int i;
		for(i=start;i<=end;i++)
		{
			namcos1_bank_element[i].bank_handler_r = hr;
			namcos1_bank_element[i].bank_handler_w = hw;
			namcos1_bank_element[i].bank_offset    = offset;
			namcos1_bank_element[i].bank_pointer   = pointer;
			offset	+= 0x2000;
			if(pointer != null) pointer.inc(0x2000);
		}
	}
	
	static void namcos1_install_rom_bank(int start,int end,int size,int offset)
	{
		UBytePtr BROM = new UBytePtr(memory_region(REGION_USER1));
		int step = size/0x2000;
		while(start < end)
		{
			namcos1_install_bank(start,start+step-1,null,rom_w,0,new UBytePtr(BROM,offset));
			start += step;
		}
	}
	
	static void namcos1_build_banks(ReadHandlerPtr key_r,WriteHandlerPtr key_w)
	{
		int i;
	
		/* S1 RAM pointer set */
		s1ram = new UBytePtr(memory_region(REGION_USER2));
	
		/* clear all banks to unknown area */
		for(i=0;i<NAMCOS1_MAX_BANK;i++)
			namcos1_install_bank(i,i,unknown_r,unknown_w,0,null);
	
		/* RAM 6 banks - palette */
		namcos1_install_bank(0x170,0x172,namcos1_paletteram_r,namcos1_paletteram_w,0,s1ram);
		/* RAM 6 banks - work ram */
		namcos1_install_bank(0x173,0x173,null,null,0,new UBytePtr(s1ram,0x6000));
		/* RAM 5 banks - videoram */
		namcos1_install_bank(0x178,0x17b,namcos1_videoram_r,namcos1_videoram_w,0,null);
		/* key chip bank (rev1_key_w / rev2_key_w ) */
		namcos1_install_bank(0x17c,0x17c,key_r,key_w,0,null);
		/* RAM 7 banks - display control, playfields, sprites */
		namcos1_install_bank(0x17e,0x17e,null,namcos1_videocontrol_w,0,new UBytePtr(s1ram,0x8000));
		/* RAM 1 shared ram, PSG device */
		namcos1_install_bank(0x17f,0x17f,soundram_r,soundram_w,0,namco_wavedata);
		/* RAM 3 banks */
		namcos1_install_bank(0x180,0x183,null,null,0,new UBytePtr(s1ram,0xc000));
		/* PRG0 */
		namcos1_install_rom_bank(0x200,0x23f,0x20000 , 0xe0000);
		/* PRG1 */
		namcos1_install_rom_bank(0x240,0x27f,0x20000 , 0xc0000);
		/* PRG2 */
		namcos1_install_rom_bank(0x280,0x2bf,0x20000 , 0xa0000);
		/* PRG3 */
		namcos1_install_rom_bank(0x2c0,0x2ff,0x20000 , 0x80000);
		/* PRG4 */
		namcos1_install_rom_bank(0x300,0x33f,0x20000 , 0x60000);
		/* PRG5 */
		namcos1_install_rom_bank(0x340,0x37f,0x20000 , 0x40000);
		/* PRG6 */
		namcos1_install_rom_bank(0x380,0x3bf,0x20000 , 0x20000);
		/* PRG7 */
		namcos1_install_rom_bank(0x3c0,0x3ff,0x20000 , 0x00000);
	}
	
	public static InitMachinePtr init_namcos1 = new InitMachinePtr() {
            public void handler() {
                
	
		int bank;
	
		/* Point all of our bankhandlers to the error handlers */
		for( bank =0 ; bank < 2*8 ; bank++ )
		{
			/* set bank pointer & handler for cpu interface */
			memory_set_bankhandler_r( bank+1,0,unknown_r);
			memory_set_bankhandler_w( bank+1,0,unknown_w);
		}
	
		/* Prepare code for Cpu 0 */
		namcos1_bankswitch(0, 0x0e00, 0x03 ); /* bank7 = 0x3ff(PRG7) */
		namcos1_bankswitch(0, 0x0e01, 0xff );
	
		/* Prepare code for Cpu 1 */
		namcos1_bankswitch(1, 0x0e00, 0x03);
		namcos1_bankswitch(1, 0x0e01, 0xff);
	
		namcos1_cpu1_banklatch = 0x03ff;
	
		/* Point mcu & sound shared RAM to destination */
		{
			UBytePtr RAM = new UBytePtr(namco_wavedata, 0x1000); /* Ram 1, bank 1, offset 0x1000 */
			cpu_setbank( 18, RAM );
			cpu_setbank( 19, RAM );
		}
	
		/* In case we had some cpu's suspended, resume them now */
		cpu_set_reset_line(1,ASSERT_LINE);
		cpu_set_reset_line(2,ASSERT_LINE);
		cpu_set_reset_line(3,ASSERT_LINE);
	
		namcos1_reset = 0;
		/* mcu patch data clear */
		mcu_patch_data = 0;
	
		berabohm_input_counter = 4;	/* for berabohm pressure sensitive buttons */
	} };
	
	
	/*******************************************************************************
	*																			   *
	*	driver specific initialize routine										   *
	*																			   *
	*******************************************************************************/
	public static class namcos1_slice_timer
	{
		public int sync_cpu;	/* synchronus cpu attribute */
		public int sliceHz;	/* slice cycle				*/
		public int delayHz;	/* delay>=0 : delay cycle	*/
						/* delay<0	: slide cycle	*/
	};
	
	public static class namcos1_specific
	{
		/* keychip */
		public int key_id_query , key_id;
		public ReadHandlerPtr key_r;
		public WriteHandlerPtr key_w;
		/* cpu slice timer */
		//const struct namcos1_slice_timer *slice_timer;
                public namcos1_slice_timer[] slice_timer;
                
                public namcos1_specific(int key_id_query , int key_id, ReadHandlerPtr key_r, WriteHandlerPtr key_w, namcos1_slice_timer[] slice_timer){
                    this.key_id_query = key_id_query;
                    this.key_id = key_id;
                    this.key_r = key_r;
                    this.key_w = key_w;
                    this.slice_timer = slice_timer;
                }
	};
	
	static void namcos1_driver_init( namcos1_specific specific )
	{
		/* keychip id */
		key_id_query = specific.key_id_query;
		key_id		 = specific.key_id;
	
		/* build bank elements */
		namcos1_build_banks(specific.key_r,specific.key_w);
	
		/* sound cpu speedup optimize (auto detect) */
		{
			UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU3)); /* sound cpu */
			int addr,flag_ptr;
	
			for(addr=0xd000;addr<0xd0ff;addr++)
			{
				if(RAM.read(addr+0)==0xb6 &&   /* lda xxxx */
				   RAM.read(addr+3)==0x27 &&   /* BEQ addr */
				   RAM.read(addr+4)==0xfb )
				{
					flag_ptr = RAM.read(addr+1)*256 + RAM.read(addr+2);
					if(flag_ptr>0x5140 && flag_ptr<0x5400)
					{
						sound_spinlock_pc	= addr+3;
						sound_spinlock_ram	= install_mem_read_handler(2,flag_ptr,flag_ptr,namcos1_sound_spinlock_r);
						logerror("Set sound cpu spinlock : pc=%04x , addr = %04x\n",sound_spinlock_pc,flag_ptr);
						break;
					}
				}
			}
		}
/*TODO*///	#if NEW_TIMER
/*TODO*///		/* all cpu's does not need synchronization to all timers */
/*TODO*///		cpu_set_full_synchronize(SYNC_NO_CPU);
/*TODO*///		{
/*TODO*///			const struct namcos1_slice_timer *slice = specific.slice_timer;
/*TODO*///			while(slice.sync_cpu != SYNC_NO_CPU)
/*TODO*///			{
/*TODO*///				/* start CPU slice timer */
/*TODO*///				cpu_start_extend_time_slice(slice.sync_cpu,
/*TODO*///					TIME_IN_HZ(slice.delayHz),TIME_IN_HZ(slice.sliceHz) );
/*TODO*///				slice++;
/*TODO*///			}
/*TODO*///		}
/*TODO*///	#else
		/* compatible with old timer system */
		timer_pulse(TIME_IN_HZ(60*25),0,null);
/*TODO*///	#endif
/*TODO*///	}
/*TODO*///	
/*TODO*///	#if NEW_TIMER
	/* normaly CPU slice optimize */
	/* slice order is 0:2:1:x:0:3:1:x */
/*TODO*///	static namcos1_slice_timer normal_slice[]={
/*TODO*///		new namcos1_slice_timer( SYNC_2CPU(0,1),60*20,-60*20*2 ),	/* CPU 0,1 20/vblank , slide slice */
/*TODO*///		{ SYNC_2CPU(2,3),60*5,-(60*5*2+60*20*4) },	/* CPU 2,3 10/vblank */
/*TODO*///		{ SYNC_NO_CPU }
	};
/*TODO*///	#else
	static namcos1_slice_timer normal_slice[]={null};
/*TODO*///	#endif
	
	/*******************************************************************************
	*	Shadowland / Youkai Douchuuki specific									   *
	*******************************************************************************/
	public static InitDriverPtr init_shadowld = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific shadowld_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(shadowld_specific);
	} };
	
	/*******************************************************************************
	*	Dragon Spirit specific													   *
	*******************************************************************************/
	public static InitDriverPtr init_dspirit = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific dspirit_specific=new namcos1_specific
		(
			0x00,0x36,						/* key query , key id */
			dspirit_key_r,dspirit_key_w,	/* key handler */
			normal_slice					/* CPU slice normal */
                );
		namcos1_driver_init(dspirit_specific);
	} };
	
	/*******************************************************************************
	*	Quester specific														   *
	*******************************************************************************/
	public static InitDriverPtr init_quester = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific quester_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(quester_specific);
	} };
	
	/*******************************************************************************
	*	Blazer specific 														   *
	*******************************************************************************/
	public static InitDriverPtr init_blazer = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific blazer_specific=new namcos1_specific
		(
			0x00,0x13,					/* key query , key id */
			blazer_key_r,blazer_key_w,	/* key handler */
			normal_slice				/* CPU slice normal */
                );
		namcos1_driver_init(blazer_specific);
	} };
	
	/*******************************************************************************
	*	Pac-Mania / Pac-Mania (Japan) specific									   *
	*******************************************************************************/
	public static InitDriverPtr init_pacmania = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific pacmania_specific=new namcos1_specific
		(
			0x4b,0x12,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(pacmania_specific);
	} };
	
	/*******************************************************************************
	*	Galaga '88 / Galaga '88 (Japan) specific								   *
	*******************************************************************************/
	public static InitDriverPtr init_galaga88 = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific galaga88_specific=new namcos1_specific
		(
			0x2d,0x31,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(galaga88_specific);
	} };
	
	/*******************************************************************************
	*	World Stadium specific													   *
	*******************************************************************************/
	public static InitDriverPtr init_ws = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific ws_specific=new namcos1_specific
		(
			0xd3,0x07,				/* key query , key id */
			ws_key_r,ws_key_w,		/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(ws_specific);
	} };
	
	/*******************************************************************************
	*	Beraboh Man specific													   *
	*******************************************************************************/
        static int[] counter=new int[4];
        static int clk;
        
	public static ReadHandlerPtr berabohm_buttons_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int res;
	
	
		if (offset == 0)
		{
			if (berabohm_input_counter == 0) res = readinputport(0);
			else
			{
				
	
				res = readinputport(4 + (berabohm_input_counter-1));
				if ((res & 0x80)!=0)
				{
					if (counter[berabohm_input_counter-1] >= 0)
	//					res = 0x40 | counter[berabohm_input_counter-1];	I can't get max power with this...
						res = 0x40 | (counter[berabohm_input_counter-1]>>1);
					else
					{
						if ((res & 0x40)!=0) res = 0x40;
						else res = 0x00;
					}
				}
				else if ((res & 0x40)!=0)
				{
					if (counter[berabohm_input_counter-1] < 0x3f)
					{
						counter[berabohm_input_counter-1]++;
						res = 0x00;
					}
					else res = 0x7f;
				}
				else
					counter[berabohm_input_counter-1] = -1;
			}
			berabohm_input_counter = (berabohm_input_counter+1) % 5;
		}
		else
		{
			
			res = 0;
			clk++;
			if ((clk & 1)!=0) res |= 0x40;
			else if (berabohm_input_counter == 4) res |= 0x10;
	
			res |= (readinputport(1) & 0x8f);
		}
	
		return res;
	} };
	
	public static InitDriverPtr init_berabohm = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific berabohm_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(berabohm_specific);
		install_mem_read_handler(3,0x1400,0x1401,berabohm_buttons_r);
	} };
	
	/*******************************************************************************
	*	Alice in Wonderland / Marchen Maze specific 							   *
	*******************************************************************************/
	public static InitDriverPtr init_alice = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific alice_specific=new namcos1_specific
		(
			0x5b,0x25,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(alice_specific);
	} };
	
	/*******************************************************************************
	*	Bakutotsu Kijuutei specific 											   *
	*******************************************************************************/
	public static InitDriverPtr init_bakutotu = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific bakutotu_specific=new namcos1_specific
		(
			0x03,0x22,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(bakutotu_specific);
	} };
	
	/*******************************************************************************
	*	World Court specific													   *
	*******************************************************************************/
	public static InitDriverPtr init_wldcourt = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific worldcourt_specific=new namcos1_specific
		(
			0x00,0x35,				/* key query , key id */
			rev2_key_r,rev2_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(worldcourt_specific);
	} };
	
	/*******************************************************************************
	*	Splatter House specific 												   *
	*******************************************************************************/
	public static InitDriverPtr init_splatter = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific splatter_specific=new namcos1_specific
		(
			0x00,0x00,						/* key query , key id */
			splatter_key_r,splatter_key_w,	/* key handler */
			normal_slice					/* CPU slice normal */
                );
		namcos1_driver_init(splatter_specific);
	} };
	
	/*******************************************************************************
	*	Face Off specific														   *
	*******************************************************************************/
	public static InitDriverPtr init_faceoff = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific faceoff_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(faceoff_specific);
	} };
	
	/*******************************************************************************
	*	Rompers specific														   *
	*******************************************************************************/
	public static InitDriverPtr init_rompers = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific rompers_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(rompers_specific);
		key[0x70] = 0xb6;
	} };
	
	/*******************************************************************************
	*	Blast Off specific														   *
	*******************************************************************************/
	public static InitDriverPtr init_blastoff = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific blastoff_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(blastoff_specific);
		key[0] = 0xb7;
	} };
	
	/*******************************************************************************
	*	World Stadium '89 specific                                                 *
	*******************************************************************************/
	public static InitDriverPtr init_ws89 = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific ws89_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(ws89_specific);
	
		key[0x20] = 0xb8;
	} };
	
	/*******************************************************************************
	*	Dangerous Seed specific 												   *
	*******************************************************************************/
	public static InitDriverPtr init_dangseed = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific dangseed_specific=new namcos1_specific
		(
			0x00,0x34,						/* key query , key id */
			dangseed_key_r,dangseed_key_w,	/* key handler */
			normal_slice					/* CPU slice normal */
                );
		namcos1_driver_init(dangseed_specific);
	} };
	
	/*******************************************************************************
	*	World Stadium '90 specific                                                 *
	*******************************************************************************/
	public static InitDriverPtr init_ws90 = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific ws90_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(ws90_specific);
	
		key[0x47] = 0x36;
		key[0x40] = 0x36;
	} };
	
	/*******************************************************************************
	*	Pistol Daimyo no Bouken specific										   *
	*******************************************************************************/
	public static InitDriverPtr init_pistoldm = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific pistoldm_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(pistoldm_specific);
		//key[0x17] = ;
		//key[0x07] = ;
		key[0x43] = 0x35;
	} };
	
	/*******************************************************************************
	*	Souko Ban DX specific													   *
	*******************************************************************************/
	public static InitDriverPtr init_soukobdx = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific soukobdx_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(soukobdx_specific);
		//key[0x27] = ;
		//key[0x07] = ;
		key[0x43] = 0x37;
	} };
	
	/*******************************************************************************
	*	Puzzle Club specific													   *
	*******************************************************************************/
	public static InitDriverPtr init_puzlclub = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific puzlclub_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(puzlclub_specific);
		key[0x03] = 0x35;
	} };
	
	/*******************************************************************************
	*	Tank Force specific 													   *
	*******************************************************************************/
	public static InitDriverPtr init_tankfrce = new InitDriverPtr() { public void handler() 
	{
		namcos1_specific tankfrce_specific=new namcos1_specific
		(
			0x00,0x00,				/* key query , key id */
			rev1_key_r,rev1_key_w,	/* key handler */
			normal_slice			/* CPU slice normal */
                );
		namcos1_driver_init(tankfrce_specific);
		//key[0x57] = ;
		//key[0x17] = ;
		key[0x2b] = 0xb9;
		key[0x50] = 0xb9;
	} };
}
