package dev.toka.signs;
/** Four-line creation syntax; business providers still validate referenced codes. */
final class SignInput {
    private SignInput(){}
    static String validate(int type,String[] lines){
        if(lines==null||lines.length!=4)return "需要四行文字。";
        for(String line:lines)if(line==null||line.length()>8192)return "文字格式不正確或過長。";
        if(!lines[2].isBlank()||!lines[3].isBlank())return "功能牌第 3、4 行請留空，顯示內容由功能產生。";
        if(type==33||type==41)return lines[1].isBlank()?null:"此功能第 2 行請留空。";
        return lines[1].isBlank()?"第 2 行需要功能代碼；請查看 /sign help。":null;
    }
}
