/**
 * ported to v0.56
 */
/**
 * Changelog
 * =========
 * 27/05/2019 ported to mame 0.56 (shadow)
 */
package WIP2.mame056.cpu.m6800;

import static WIP2.mame056.cpu.m6800.m6800H.*;
import static WIP2.mame056.cpuintrfH.*;

public class m6808 extends m6800 {

    public m6808() {
        cpu_num = CPU_M6808;
        num_irqs = 1;
        default_vector = 0;
        icount = m6800_ICount;
        overclock = 1.00;
        irq_int = M6808_IRQ_LINE;
        databus_width = 8;
        pgm_memory_base = 0;
        address_shift = 0;
        address_bits = 16;
        endianess = CPU_IS_BE;
        align_unit = 1;
        max_inst_len = 4;
    }

    @Override
    public void reset(Object param) {//NOTE: for some reason it is not properly set on init (probably got erased from set_context
        super.reset(param);
        m6800.insn = m6800_insn;
        m6800.cycles = cycles_6800;
    }

    public String cpu_info(Object context, int regnum) {
        switch (regnum) {
            case CPU_INFO_NAME:
                return "M6808";
        }
        return super.cpu_info(context, regnum);
    }
}
