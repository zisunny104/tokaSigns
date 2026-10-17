package dev.toka.signs;
import org.junit.Test;
import static org.junit.Assert.*;
public class SignInputTest {
 @Test public void fourLineSyntaxDoesNotDiscardUserText(){
  assertNull(SignInput.validate(11,new String[]{"tpp","spawn","",""}));
  assertNotNull(SignInput.validate(11,new String[]{"tpp","","",""}));
  assertNotNull(SignInput.validate(11,new String[]{"tpp","spawn","不要遺失",""}));
  assertNull(SignInput.validate(41,new String[]{"status","","",""}));
  assertNotNull(SignInput.validate(41,new String[]{"status","unexpected","",""}));
 }
}
