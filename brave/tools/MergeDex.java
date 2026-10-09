import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.value.*;
import org.jf.dexlib2.immutable.instruction.*;
import org.jf.dexlib2.immutable.value.*;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.rewriter.*;
import org.jf.dexlib2.writer.pool.DexPool;
import java.io.*;
import java.util.*;

/** Rewrites string constants through dexlib so sorted indices and checksums remain valid. */
public final class MergeDex {
    public static void main(String[] args) throws Exception {
        Opcodes opcodes=Opcodes.forApi(29);
        DexFile original=DexFileFactory.loadDexFile(new File(args[0]),opcodes);
        Map<String,ClassDef> replacements=new HashMap<>();
        if(args.length>2){DexFile patch=DexFileFactory.loadDexFile(new File(args[2]),opcodes);for(ClassDef c:patch.getClasses())replacements.put(c.getType(),c);}
        DexRewriter rewriter=new DexRewriter(new RewriterModule(){
            @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rewriters){
                return new InstructionRewriter(rewriters){
                    @Override public Instruction rewrite(Instruction instruction){
                        if(instruction instanceof ReferenceInstruction){
                            Reference ref=((ReferenceInstruction)instruction).getReference();
                            if(ref instanceof StringReference){
                                ImmutableStringReference string=new ImmutableStringReference(((StringReference)ref).getString().replace("com.brave.browser","app.anibrave.main"));
                                int register=((OneRegisterInstruction)instruction).getRegisterA();
                                if(instruction.getOpcode()==Opcode.CONST_STRING)return new ImmutableInstruction21c(instruction.getOpcode(),register,string);
                                if(instruction.getOpcode()==Opcode.CONST_STRING_JUMBO)return new ImmutableInstruction31c(instruction.getOpcode(),register,string);
                            }
                        }
                        return super.rewrite(instruction);
                    }
                };
            }
            @Override public Rewriter<EncodedValue> getEncodedValueRewriter(Rewriters rewriters){
                return new EncodedValueRewriter(rewriters){
                    @Override public EncodedValue rewrite(EncodedValue value){
                        if(value instanceof StringEncodedValue)return new ImmutableStringEncodedValue(((StringEncodedValue)value).getValue().replace("com.brave.browser","app.anibrave.main"));
                        return super.rewrite(value);
                    }
                };
            }
        });
        DexPool pool=new DexPool(opcodes);
        int replaced=0;
        for(ClassDef c:original.getClasses()){
            ClassDef replacement=replacements.remove(c.getType());if(replacement!=null){replaced++;continue;}
            pool.internClass(rewriter.getClassDefRewriter().rewrite(c));
        }
        if(!replacements.isEmpty())throw new IllegalStateException("Patch class missing from upstream: "+replacements.keySet());
        pool.writeTo(new FileDataStore(new File(args[1])));
        System.out.println(new File(args[1]).getName()+": "+original.getClasses().size()+" classes, "+replaced+" replacements");
    }
}
