/***************************************************************************

Atari Night Driver machine

If you have any questions about how this driver works, don't hesitate to
ask.  - Mike Balfour (mab22@po.cwru.edu)
***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.machine;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.inptport.*;

public class nitedrvr
{
	
	public static UBytePtr nitedrvr_ram = new UBytePtr();
	
	public static int nitedrvr_gear = 1;
	public static int nitedrvr_track = 0;
	
	static int nitedrvr_steering_buf = 0;
	static int nitedrvr_steering_val = 0x00;
	
	/***************************************************************************
	nitedrvr_ram_r
	***************************************************************************/
	public static ReadHandlerPtr nitedrvr_ram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return nitedrvr_ram.read(offset);
	} };
	
	/***************************************************************************
	nitedrvr_ram_w
	***************************************************************************/
	public static WriteHandlerPtr nitedrvr_ram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		nitedrvr_ram.write(offset, data);
	} };
	
	/***************************************************************************
	Steering
	
	When D7 is high, the steering wheel has moved.
	If D6 is low, it moved left.  If D6 is high, it moved right.
	Be sure to keep returning a direction until steering_reset is called,
	because D6 and D7 are apparently checked at different times, and a
	change in-between can affect the direction you move.
	***************************************************************************/
        static int last_val=0;
        
	static int nitedrvr_steering()
	{
		
		int this_val;
		int delta;
	
		this_val=input_port_5_r.handler(0);
	
		delta=this_val-last_val;
		last_val=this_val;
		if (delta>128) delta-=256;
		else if (delta<-128) delta+=256;
		/* Divide by four to make our steering less sensitive */
		nitedrvr_steering_buf+=(delta/4);
	
		if (nitedrvr_steering_buf>0)
		{
			nitedrvr_steering_buf--;
			nitedrvr_steering_val=0xC0;
		}
		else if (nitedrvr_steering_buf<0)
		{
			nitedrvr_steering_buf++;
			nitedrvr_steering_val=0x80;
		}
		else
		{
			nitedrvr_steering_val=0x00;
		}
	
		return nitedrvr_steering_val;
	}
	
	/***************************************************************************
	nitedrvr_steering_reset
	***************************************************************************/
	public static ReadHandlerPtr nitedrvr_steering_reset_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		nitedrvr_steering_val=0x00;
		return 0;
	} };
	
	public static WriteHandlerPtr nitedrvr_steering_reset_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		nitedrvr_steering_val=0x00;
	} };
	
	
	/***************************************************************************
	nitedrvr_in0_r
	
	Night Driver looks for the following:
		A: $00
			D4 - OPT1
			D5 - OPT2
			D6 - OPT3
			D7 - OPT4
		A: $01
			D4 - TRACK SET
			D5 - BONUS TIME ALLOWED
			D6 - VBLANK
			D7 - !TEST
		A: $02
			D4 - !GEAR 1
			D5 - !GEAR 2
			D6 - !GEAR 3
			D7 - SPARE
		A: $03
			D4 - SPARE
			D5 - DIFFICULT BONUS
			D6 - STEER A
			D7 - STEER B
	
	Fill in the steering and gear bits in a special way.
	***************************************************************************/
	
	public static ReadHandlerPtr nitedrvr_in0_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int gear;
	
		gear=input_port_2_r.handler(0);
		if ((gear & 0x10)!=0)				nitedrvr_gear=1;
		else if ((gear & 0x20)!=0)			nitedrvr_gear=2;
		else if ((gear & 0x40)!=0)			nitedrvr_gear=3;
		else if ((gear & 0x80)!=0)			nitedrvr_gear=4;
	
		switch (offset & 0x03)
		{
			case 0x00:						/* No remapping necessary */
				return input_port_0_r.handler(0);
			case 0x01:						/* No remapping necessary */
				return input_port_1_r.handler(0);
			case 0x02:						/* Remap our gear shift */
				if (nitedrvr_gear==1)		return 0xE0;
				else if (nitedrvr_gear==2)	return 0xD0;
				else if (nitedrvr_gear==3)	return 0xB0;
				else						return 0x70;
			case 0x03:						/* Remap our steering */
				return (input_port_3_r.handler(0) | nitedrvr_steering());
			default:
				return 0xFF;
		}
	} };
	
	/***************************************************************************
	nitedrvr_in1_r
	
	Night Driver looks for the following:
		A: $00
			D6 - SPARE
			D7 - COIN 1
		A: $01
			D6 - SPARE
			D7 - COIN 2
		A: $02
			D6 - SPARE
			D7 - !START
		A: $03
			D6 - SPARE
			D7 - !ACC
		A: $04
			D6 - SPARE
			D7 - EXPERT
		A: $05
			D6 - SPARE
			D7 - NOVICE
		A: $06
			D6 - SPARE
			D7 - Special Alternating Signal
		A: $07
			D6 - SPARE
			D7 - Ground
	
	Fill in the track difficulty switch and special signal in a special way.
	***************************************************************************/
	static int ac_line=0x00;
        
	public static ReadHandlerPtr nitedrvr_in1_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		
		int port;
	
		ac_line=(ac_line+1) % 3;
	
		port=input_port_4_r.handler(0);
		if ((port & 0x10)!=0)				nitedrvr_track=0;
		else if ((port & 0x20)!=0)			nitedrvr_track=1;
		else if ((port & 0x40)!=0)			nitedrvr_track=2;
	
		switch (offset & 0x07)
		{
			case 0x00:
				return ((port & 0x01) << 7);
			case 0x01:
				return ((port & 0x02) << 6);
			case 0x02:
				return ((port & 0x04) << 5);
			case 0x03:
				return ((port & 0x08) << 4);
			case 0x04:
				if (nitedrvr_track == 1) return 0x80; else return 0x00;
			case 0x05:
				if (nitedrvr_track == 0) return 0x80; else return 0x00;
			case 0x06:
				/* TODO: fix alternating signal? */
				if (ac_line==0) return 0x80; else return 0x00;
			case 0x07:
				return 0x00;
			default:
				return 0xFF;
		}
	} };
	
	/***************************************************************************
	nitedrvr_out0_w
	
	Sound bits:
	
	D0 = !SPEED1
	D1 = !SPEED2
	D2 = !SPEED3
	D3 = !SPEED4
	D4 = SKID1
	D5 = SKID2
	***************************************************************************/
	public static WriteHandlerPtr nitedrvr_out0_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/* TODO: put sound bits here */
	} };
	
	/***************************************************************************
	nitedrvr_out1_w
	
	D0 = !CRASH - also drives a video invert signal
	D1 = ATTRACT
	D2 = Spare (Not used)
	D3 = Not used?
	D4 = LED START
	D5 = Spare (Not used)
	***************************************************************************/
	public static WriteHandlerPtr nitedrvr_out1_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/*TODO*///set_led_status(0,data & 0x10);
		/* TODO: put sound bits here */
	} };
	
}
