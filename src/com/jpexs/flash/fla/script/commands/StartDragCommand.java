package com.jpexs.flash.fla.script.commands;

import com.jpexs.flash.fla.script.Expression;
import com.jpexs.flash.fla.converter.FlaFormatVersion;

/**
 *
 * @author JPEXS
 */
public class StartDragCommand extends AbstractCommand {

    private final Expression target;
    private final boolean constrainToRectangle;
    private final Expression rectLeft;
    private final Expression rectTop;
    private final Expression rectRight;
    private final Expression rectBottom;
    private final boolean lockMouseToCenter;

    public StartDragCommand(Expression target, Expression rectLeft, Expression rectTop, Expression rectRight, Expression rectBottom, boolean lockMouseToCenter) {
        this.target = target;
        this.constrainToRectangle = true;
        this.rectLeft = rectLeft;
        this.rectTop = rectTop;
        this.rectRight = rectRight;
        this.rectBottom = rectBottom;
        this.lockMouseToCenter = lockMouseToCenter;
    }
    
    public StartDragCommand(Expression target, boolean lockMouseToCenter) {
        this.target = target;
        this.constrainToRectangle = false;
        this.rectLeft = new Expression();
        this.rectTop = new Expression();
        this.rectRight = new Expression();
        this.rectBottom = new Expression();
        this.lockMouseToCenter = lockMouseToCenter;
    }
    
    @Override
    public int getActionKind() {
        return FLA4_ACTION_START_DRAG;
    }
    
    @Override
    public FlaFormatVersion getMinFlaVersion() {
        return FlaFormatVersion.F4;
    }

    @Override
    public Expression getTarget() {
        return target;
    }

    @Override
    public Expression getArg0() {
        return rectLeft;
    }

    @Override
    public Expression getArg1() {
        return rectTop;
    }

    @Override
    public Expression getArg2() {
        return rectRight;
    }

    @Override
    public Expression getArg3() {
        return rectBottom;
    }

    public boolean isConstrainToRectangle() {
        return constrainToRectangle;
    }

    public boolean isLockMouseToCenter() {
        return lockMouseToCenter;
    }

    @Override
    public String toString() {
        String ret = "Start Drag (";
        ret += target;
        if (constrainToRectangle) {
            ret += ", L=" + rectLeft + ", T=" + rectTop + ", R=" + rectRight + ", B=" + rectBottom;
        }
        if (lockMouseToCenter) {
            ret += ", lockcenter";
        }
        ret += ")";
        return ret;
    }        
}
