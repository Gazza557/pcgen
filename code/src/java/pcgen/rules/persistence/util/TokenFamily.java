/*
 * Copyright 2008 (C) Tom Parker <thpr@users.sourceforge.net>
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package pcgen.rules.persistence.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import pcgen.base.lang.CaseInsensitiveString;
import pcgen.base.lang.UnreachableError;
import pcgen.base.util.CaseInsensitiveMap;
import pcgen.base.util.DoubleKeyMap;
import pcgen.base.util.TripleKeyMap;
import pcgen.cdom.base.GroupDefinition;
import pcgen.cdom.base.Loadable;
import pcgen.persistence.lst.prereq.PrerequisiteParserInterface;
import pcgen.rules.persistence.token.CDOMSecondaryToken;
import pcgen.rules.persistence.token.CDOMSubToken;
import pcgen.rules.persistence.token.CDOMToken;
import pcgen.rules.persistence.token.DeferredToken;
import pcgen.util.Logging;

/**
 * A TokenFamily represents a set of tokens applicable to a specific Revision of
 * PCGen. This class also acts as the overall library of TokenFamily objects.
 */
public final class TokenFamily implements Comparable<TokenFamily>
{

	/**
	 * The TokenFamily for the current version of PCGen (the one being run)
	 */
	public static final TokenFamily CURRENT = new TokenFamily(new Revision(
			Integer.MAX_VALUE, 0, 0));

	/**
	 * The TokenFamily for Revision 5.14 of PCGen. This version has specific
	 * compatibility around things like using PCClass tokens on PCClassLevel
	 * lines, so this revision requires some hardcoding (vs other Revisions that
	 * are dynamically generated by compatibility tokens)
	 */
	public static final TokenFamily REV514 = new TokenFamily(new Revision(5,
			14, Integer.MIN_VALUE));

	/**
	 * The Library of TokenFamily objects available for use
	 */
	private static SortedMap<Revision, TokenFamily> typeMap;

	/**
	 * The Revision of PCGen for which this TokenFamily is used or is providing
	 * compatibility
	 */
	private final Revision rev;

	/**
	 * The CDOMTokens available in this TokenFamily. This is stored by the class
	 * where the token can be used and the token name.
	 */
	private final DoubleKeyMap<Class<?>, String, CDOMToken<?>> tokenMap =
			new DoubleKeyMap<Class<?>, String, CDOMToken<?>>();

	/**
	 * The CDOMSecondaryTokens available in this TokenFamily. This is stored by
	 * the class where the token can be used, the "primary/parent" token name
	 * and the subtoken name.
	 */
	private final TripleKeyMap<Class<?>, String, String, CDOMSecondaryToken<?>> subTokenMap =
			new TripleKeyMap<Class<?>, String, String, CDOMSecondaryToken<?>>(HashMap.class, CaseInsensitiveMap.class, CaseInsensitiveMap.class);

	/**
	 * The PRExxx tokens available in this TokenFamily. This is stored by the
	 * token name.
	 */
	private final Map<CaseInsensitiveString, PrerequisiteParserInterface> preTokenMap =
			new HashMap<CaseInsensitiveString, PrerequisiteParserInterface>();

	/**
	 * The DeferredTokens defined for this TokenFamily. This set of tokens will
	 * be run after the initial pass of the data load is complete
	 */
	private final List<DeferredToken<? extends Loadable>> deferredTokenList =
			new ArrayList<DeferredToken<? extends Loadable>>();

	/**
	 * The Group Definitions available for this TokenFamily. These are FACT and
	 * FACTSET items. These are stored by the Class in which they are usable and
	 * the Token name.
	 */
	private final DoubleKeyMap<Class<?>, String, GroupDefinition<?>> groupDefinitionMap =
			new DoubleKeyMap<Class<?>, String, GroupDefinition<?>>(HashMap.class, CaseInsensitiveMap.class);
	
	/**
	 * Constructs a new TokenFamily for the given Revision.
	 * 
	 * @param r The Revision of PCGen covered by this TokenFamily
	 */
	public TokenFamily(Revision r)
	{
		rev = r;
	}

	/**
	 * Adds a new Token to this TokenLibrary.
	 * 
	 * @param tok
	 *            The CDOMToken to be added to this TokenLibrary
	 * 
	 * @return The previous CDOMToken stored in this TokenLibrary for the same
	 *         Token Class and Token Name; null if no CDOMToken had previously
	 *         been stored for that combination
	 */
	@SuppressWarnings("unchecked")
	public <T> CDOMToken<T> putToken(CDOMToken<T> tok)
	{
		if (tok.getTokenClass() == null)
		{
			Logging.errorPrint("Cannot load token "
				+ tok.getClass().getSimpleName() + " with no token class");
		}
		return (CDOMToken<T>) tokenMap.put(tok.getTokenClass(), tok
				.getTokenName(), tok);
	}

	/**
	 * Returns the CDOMToken for the given Class and with the given token name.
	 * 
	 * @param cl
	 *            The Class for which a token should be retrieved
	 * @param name
	 *            The name of the token to be retrieved
	 * @return the CDOMToken for the given Class and with the given token name
	 */
	public CDOMToken<?> getToken(Class<?> cl, String name)
	{
		return tokenMap.get(cl, name);
	}

	/**
	 * Returns all the tokens in this TokenLibrary for the given Class.
	 * 
	 * @param cl
	 *            The Class for which the tokens in this library should be
	 *            returned
	 * 
	 * @return A set of the tokens in this TokenLibrary for the given Class
	 */
	public Set<CDOMToken<?>> getTokens(Class<?> cl)
	{
		return tokenMap.values(cl);
	}

	/**
	 * Adds a new SubToken to this TokenLibrary.
	 * 
	 * @param tok
	 *            The CDOMSecondaryToken to be added to this TokenLibrary
	 * 
	 * @return The previous CDOMSecondaryToken stored in this TokenLibrary for
	 *         the same Token Class, Parent Token, and Token Name; null if no
	 *         CDOMSecondaryToken had previously been stored for that
	 *         combination
	 */
	@SuppressWarnings("unchecked")
	public <U, T extends CDOMSecondaryToken<U>> CDOMSecondaryToken<U> putSubToken(T tok)
	{
		if (tok.getTokenClass() == null)
		{
			Logging.errorPrint("Cannot load token "
				+ tok.getClass().getSimpleName() + " with no token class");
		}
		return (CDOMSecondaryToken<U>) subTokenMap.put(tok.getTokenClass(), tok
				.getParentToken(), tok.getTokenName(), tok);
	}

	/**
	 * Returns the CDOMSubToken for the given Class and parent token, and with
	 * the given token name.
	 * 
	 * @param cl
	 *            The Class for which a token should be retrieved
	 * @param token
	 *            The name of the parent token for the CDOMSubToken to be
	 *            returned
	 * @param key
	 *            The name of the sub token to be retrieved
	 * @return the CDOMSubToken for the given Class and parent token, and with
	 *         the given token name
	 */
	@SuppressWarnings("unchecked")
	public <T> CDOMSubToken<? super T> getSubToken(Class<? extends T> cl, String token,
			String key)
	{
		return (CDOMSubToken<? super T>) subTokenMap.get(cl, token, key);
	}

	/**
	 * Returns all the tokens in this TokenLibrary for the given Class and
	 * parent token name.
	 * 
	 * @param cl
	 *            The Class for which the tokens in this library should be
	 *            returned
	 * @param token
	 *            The name of the parent token for the CDOMSubTokens to be
	 *            returned
	 * 
	 * @return A Set of the subtokens in this TokenLibrary for the given Class
	 *         and parent token name
	 */
	@SuppressWarnings("unchecked")
	public <T> Set<CDOMSecondaryToken<? super T>> getSubTokens(Class<? super T> cl, String token)
	{
		return (Set) subTokenMap.values(cl, token);
	}

	/**
	 * Adds a Prerequisite Parser Token to this TokenLibrary.
	 * 
	 * @param token
	 *            The Prerequisite Parser Token to be added to this TokenLibrary
	 */
	public void putPrerequisiteToken(PrerequisiteParserInterface token)
	{
		for (String s : token.kindsHandled())
		{
			preTokenMap.put(new CaseInsensitiveString(s), token);
		}
	}

	/**
	 * Returns the Prerequisite Parser Token for the given Prerequisite token
	 * name.
	 * 
	 * @param key
	 *            The Prerequisite token name for which the Prerequisite Parser
	 *            Token should be returned
	 * @return The Prerequisite Parser Token for the given Prerequisite token
	 *         name
	 */
	public PrerequisiteParserInterface getPrerequisiteToken(String key)
	{
		return preTokenMap.get(new CaseInsensitiveString(key));
	}

	/**
	 * Constructs a new TokenFamily with the given primary, secondary and
	 * tertiary values as the Sequence characteristics
	 * 
	 * @return The new TokenFamily built with the given primary, secondary and
	 *         tertiary values
	 */
	public static TokenFamily getConstant(int primary, int secondary,
			int tertiary)
	{
		if (typeMap == null)
		{
			buildMap();
		}
		Revision r = new Revision(primary, secondary, tertiary);
		TokenFamily o = typeMap.get(r);
		if (o == null)
		{
			o = new TokenFamily(r);
			typeMap.put(r, o);
		}
		return o;
	}

	/**
	 * Actually build the set of Constants, using any "public static final"
	 * constants within the child (extending) class as initial values in the
	 * Constant pool.
	 */
	private static void buildMap()
	{
		typeMap = new TreeMap<Revision, TokenFamily>();
		Class<TokenFamily> cl = TokenFamily.class;
		Field[] fields = cl.getDeclaredFields();
		for (int i = 0; i < fields.length; i++)
		{
			int mod = fields[i].getModifiers();

			if (Modifier.isStatic(mod) && Modifier.isFinal(mod)
					&& Modifier.isPublic(mod))
			{
				try
				{
					Object o = fields[i].get(null);
					if (cl.equals(o.getClass()))
					{
						TokenFamily tObj = cl.cast(o);
						if (typeMap.containsKey(tObj.rev))
						{
							throw new UnreachableError(
									"Attempt to redefine constant value "
											+ tObj.rev + " to "
											+ fields[i].getName()
											+ ", value was "
											+ typeMap.get(tObj.rev));
						}
						typeMap.put(tObj.rev, tObj);
					}
				}
				catch (IllegalArgumentException e)
				{
					throw new UnreachableError(
						"Attempt to fetch field failed: " + e.getMessage());
				}
				catch (IllegalAccessException e)
				{
					throw new UnreachableError(
						"Attempt to fetch field failed for access: "
							+ e.getMessage());
				}
			}
		}
	}

	/**
	 * Clears all of the Constants defined by this class. Note that this does
	 * not remove any Constants declared in the Constant class (as those are
	 * considered 'permanent' members of the Sequenced Constant collection.
	 * 
	 * Note that this *will not* reset the ordinal count, because that is a
	 * dangerous operation. As there could be outstanding references to
	 * constants that would be removed from the Constant pool, no reuse of
	 * ordinals is driven by this method. As a result, calling this method may
	 * result in a Constant Pool which does not have sequentially numbered
	 * ordinal values.
	 */
	public static void clearConstants()
	{
		buildMap();
	}

	/**
	 * Returns a Collection of all of the Constants for this class. The returned
	 * Collection is unmodifiable.
	 * 
	 * @return an unmodifiable Collection of all of the Constants for this class
	 */
	public static Collection<TokenFamily> getAllConstants()
	{
		return Collections.unmodifiableCollection(typeMap.values());
	}

	/**
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(TokenFamily tf)
	{
		return rev.compareTo(tf.rev);
	}

	/*
	 * Note there is no reason to do .hashCode or .equals because this is Type
	 * Safe (meaning it can only build one object per Revision)
	 */

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return "Token Family: " + rev.toString();
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		return obj == this || obj instanceof TokenFamily
				&& compareTo((TokenFamily) obj) == 0;
	}

	/**
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return rev.hashCode();
	}

	public void clearTokens()
	{
		tokenMap.clear();
		subTokenMap.clear();
		deferredTokenList.clear();
		preTokenMap.clear();
	}

	/**
	 * Returns all the DeferredTokens in this TokenLibrary.
	 * 
	 * @return A List of the DeferredToken in this TokenLibrary
	 */
	public List<DeferredToken<? extends Loadable>> getDeferredTokens()
	{
		return new ArrayList<DeferredToken<? extends Loadable>>(
				deferredTokenList);
	}

	/**
	 * Adds a new DeferredToken to this TokenLibrary.
	 * 
	 * @param newToken
	 *            The DeferredToken to be added to this TokenLibrary
	 */
	public void addDeferredToken(DeferredToken<?> newToken)
	{
		deferredTokenList.add(newToken);
	}

	/**
	 * Adds a new GroupDefinition to this TokenFamily. A GroupDefinition can
	 * produce an ObjectContainer (grouping of objects) based on an underlying
	 * set of requirements (typically defined in the Data Control file).
	 * 
	 * @param def
	 *            The GroupDefinition to be added to this TokenFamily.
	 */
	public void addGroupDefinition(GroupDefinition<?> def)
	{
		GroupDefinition<?> existingDef =
				groupDefinitionMap.put(def.getReferenceClass(),
					def.getPrimitiveName(), def);
		if (existingDef != null)
		{
			Logging.errorPrint("Duplicate Group Definition in "
				+ def.getReferenceClass().getSimpleName() + ": "
				+ def.getPrimitiveName() + ". Classes were "
				+ existingDef.getClass().getName() + " and "
				+ def.getClass().getName());
		}
	}

	/**
	 * Returns the GroupDefinition for the given Class, and with the given group
	 * name.
	 * 
	 * @param cl
	 *            The Class for which a token should be retrieved
	 * @param name
	 *            The name of the GroupDefinition to be retrieved
	 * @return the GroupDefinition for the given Class, and with the given group
	 *         name
	 */
	@SuppressWarnings("unchecked")
	public <T> GroupDefinition<T> getGroup(Class<T> cl, String name)
	{
		return (GroupDefinition<T>) groupDefinitionMap.get(cl, name);
	}
}
