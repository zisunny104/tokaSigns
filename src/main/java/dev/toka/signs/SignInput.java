package dev.toka.signs;
/** Four-line creation syntax; business providers still validate referenced codes. Returns a message key (lang file) or null when valid. */
final class SignInput {
    private SignInput(){}
    static String validate(int type,String[] lines){
        if(lines==null||lines.length!=4)return "err.input.fourLines";
        for(String line:lines)if(line==null||line.length()>8192)return "err.input.badFormat";
        if(!lines[2].isBlank()||!lines[3].isBlank())return "err.input.leaveBlank34";
        if(type==33||type==41)return lines[1].isBlank()?null:"err.input.leaveBlank2";
        return lines[1].isBlank()?"err.input.needCode":null;
    }
}
