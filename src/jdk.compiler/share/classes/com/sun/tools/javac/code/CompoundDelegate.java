/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sun.tools.javac.code;

import java.util.function.Predicate;
import com.sun.tools.javac.code.Scope.ScopeListener;
import com.sun.tools.javac.code.Scope.LookupKind;
import com.sun.tools.javac.util.Name;

/**
 *
 * @author jlahoda
 */
public abstract class CompoundDelegate {

    public abstract void addListener(ScopeListener l);

    public abstract Iterable<Symbol> getSymbols(Predicate<Symbol> sf, LookupKind lookupKind);

    public abstract Iterable<Symbol> getSymbolsByName(Name name, Predicate<Symbol> sf, LookupKind lookupKind);

    public abstract boolean includes(Symbol sym);

    public abstract Scope getOrigin(Symbol sym);

    public abstract boolean isStaticallyImported(Symbol sym);

    public abstract Scope getDelegate();

}
