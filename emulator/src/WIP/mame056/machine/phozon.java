/***************************************************************************

  machine.c

  Functions to emulate general aspects of the machine (RAM, ROM, interrupts,
  I/O ports)

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.machine;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.inptport.*;

public class phozon
{
	
	public static UBytePtr phozon_snd_sharedram = new UBytePtr();
	public static UBytePtr phozon_spriteram = new UBytePtr();
	public static UBytePtr phozon_customio_1 = new UBytePtr(), phozon_customio_2 = new UBytePtr();
	static int credits, coincounter1, coincounter2;
	
	public static InitMachinePtr phozon_init_machine = new InitMachinePtr() { public void handler() 
	{
	    credits = coincounter1 = coincounter2 = 0;
		cpu_set_halt_line(1, CLEAR_LINE);
		cpu_set_halt_line(2, CLEAR_LINE);
	} };
	
	/* memory handlers */
	public static ReadHandlerPtr phozon_spriteram_r  = new ReadHandlerPtr() { public int handler(int offset){
	    return phozon_spriteram.read(offset);
	} };
	
	public static WriteHandlerPtr phozon_spriteram_w = new WriteHandlerPtr() {public void handler(int offset, int data){
	   phozon_spriteram.write(offset, data);
	} };
	
	public static ReadHandlerPtr phozon_snd_sharedram_r  = new ReadHandlerPtr() { public int handler(int offset){
	    return phozon_snd_sharedram.read(offset);
	} };
	
	public static WriteHandlerPtr phozon_snd_sharedram_w = new WriteHandlerPtr() {public void handler(int offset, int data){
	    phozon_snd_sharedram.write(offset, data);
	} };
	
	/* cpu control functions */
	public static WriteHandlerPtr phozon_cpu2_enable_w = new WriteHandlerPtr() {public void handler(int offset, int data){
		cpu_set_halt_line(1, offset!=0 ? CLEAR_LINE : ASSERT_LINE);
	} };
	
	public static WriteHandlerPtr phozon_cpu3_enable_w = new WriteHandlerPtr() {public void handler(int offset, int data){
		cpu_set_halt_line(2, offset!=0 ? CLEAR_LINE : ASSERT_LINE);
	} };
	
	public static WriteHandlerPtr phozon_cpu3_reset_w = new WriteHandlerPtr() {public void handler(int offset, int data){
		cpu_set_reset_line(2,PULSE_LINE);
	} };
	
	/************************************************************************************
	*																					*
	*           Phozon custom I/O chips (preliminary)										*
	*																					*
	************************************************************************************/
	
	public static WriteHandlerPtr phozon_customio_1_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		phozon_customio_1.write(offset, data);
	} };
	
	public static WriteHandlerPtr phozon_customio_2_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    phozon_customio_2.write(offset, data);
	} };
	
	static int credmoned [] = { 1, 1, 1, 1, 1, 2, 2, 3 };
	static int monedcred [] = { 1, 2, 3, 6, 7, 1, 3, 1 };
        
        static int lastval;
	
	public static ReadHandlerPtr phozon_customio_1_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
	    int mode, val, temp1, temp2;
	
	    mode = phozon_customio_1.read(8);
	    if (mode == 3)	/* normal mode */
	    {
	        switch (offset)
	        {
	            case 0:     /* Coin slots */
	            {
	                
	
	                val = (readinputport( 2 ) >> 4) & 0x03;
	                temp1 = readinputport( 0 ) & 0x07;
	                temp2 = (readinputport( 0 ) >> 5) & 0x07;
	
	                /* bit 0 is a trigger for the coin slot 1 */
	                if ((val & 1)!=0 && ((val ^ lastval) & 1)!=0)
	                {
	                    coincounter1++;
	                    if (coincounter1 >= credmoned[temp1])
	                    {
	                        credits += monedcred [temp1];
	                        coincounter1 -= credmoned [temp1];
	                    }
	                }
	                /* bit 1 is a trigger for the coin slot 2 */
	                if ((val & 2)!=0 && ((val ^ lastval) & 2)!=0)
	                {
	                    coincounter2++;
	                    if (coincounter2 >= credmoned[temp2])
	                    {
	                        credits += monedcred [temp2];
	                        coincounter2 -= credmoned [temp2];
	                    }
	                }
	
	                if (credits > 99)
	                    credits = 99;
	
	                return lastval = val;
	            }
	                //break;
	            case 1:
	            {
	                val = readinputport( 2 ) & 0x03;
	                temp1 = readinputport( 0 ) & 0x07;
	                temp2 = (readinputport( 0 ) >> 5) & 0x07;
	
	                /* bit 0 is a trigger for the 1 player start */
	                if ((val & 1)!=0 && ((val ^ lastval) & 1)!=0)
	                {
	                    if (credits > 0)
	                        credits--;
	                    else
	                        val &= ~1;   /* otherwise you can start with no credits! */
	                }
	                /* bit 1 is a trigger for the 2 player start */
	                if ((val & 2)!=0 && ((val ^ lastval) & 2)!=0)
	                {
	                    if (credits >= 2)
	                        credits -= 2;
	                    else
	                        val &= ~2;   /* otherwise you can start with no credits! */
	                }
	                return lastval = val;
	            }
	                //break;
	            case 2:
	                return (credits / 10);      /* high BCD of credits */
	                //break;
	            case 3:
	                return (credits % 10);      /* low BCD of credits */
	                //break;
	            case 4:
	                return (readinputport( 3 ) & 0x0f);   /* 1P controls */
	                //break;
	            case 5:
	                return (readinputport( 4 ) & 0x03);   /* 1P button 1 */
	                //break;
	            default:
	                return 0x0;
	        }
	    }
	    else if (mode == 5)	/* IO tests */
	    {
	        switch (offset)
	        {
				case 0x00: val = 0x00; break;
				case 0x01: val = 0x02; break;
				case 0x02: val = 0x03; break;
				case 0x03: val = 0x04; break;
				case 0x04: val = 0x05; break;
				case 0x05: val = 0x06; break;
				case 0x06: val = 0x0c; break;
				case 0x07: val = 0x0a; break;
	            default:
					val = phozon_customio_1.read(offset);
	        }
	    }
		else if (mode == 1)	/* test mode controls */
	    {
	        switch (offset)
	        {
				case 4:
					return (readinputport( 2 ) & 0x03);	/* start 1 & 2 */
					//break;
				case 5:
					return (readinputport( 3 ) &0x0f);	/* 1P controls */
					//break;
				case 7:
					return (readinputport( 4 ) & 0x03);	/* 1P button 1 */
					//break;
				default:
					return phozon_customio_1.read(offset);
	        }
	    }
		else
			val = phozon_customio_1.read(offset);
	    return val;
	} };
	
	public static ReadHandlerPtr phozon_customio_2_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
	    int mode, val;
	
	    mode = phozon_customio_2.read(8);
	    if (mode == 8)	/* IO tests */
	    {
	        switch (offset)
	        {
				case 0x00: val = 0x01; break;
				case 0x01: val = 0x0c; break;
	            default:
					val = phozon_customio_2.read(offset);
	        }
	    }
		else if (mode == 9)
	    {
	        switch (offset)	/* TODO: coinage B & check bonus life bits */
	        {
	            case 0:
	                val = (readinputport( 0 ) & 0x08) >> 3;		/* lives (bit 0) */
					val |= (readinputport( 0 ) & 0x01) << 2;	/* coinage A (bit 0) */
					val |= (readinputport( 0 ) & 0x04) << 1;	/* coinage A (bit 2) */
	                break;
	            case 1:
					val = (readinputport( 0 ) & 0x10) >> 4;		/* lives (bit 1) */
					val |= (readinputport( 1 ) & 0xc0) >> 5;	/* bonus life (bits 1 & 0) */
					val |= (readinputport( 0 ) & 0x02) << 2;	/* coinage A (bit 1) */
	                break;
				case 2:
					val = (readinputport( 1 ) & 0x07) << 1;		/* rank */
	                break;
				case 4:	/* some bits of coinage B (not implemented yet) */
					val = 0;
	                break;
	            case 6:
	                val = readinputport( 1 ) & 0x08;			/* test mode */
					val |= (readinputport( 2 ) & 0x80) >> 5;	/* cabinet */
	                break;
	            default:
					val = phozon_customio_2.read(offset);
	        }
		}
		else
			val = phozon_customio_2.read(offset);
		return val;
	} };
}
