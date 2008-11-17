/*
 * Copyright (c) 2008 Tom Parker <thpr@users.sourceforge.net>
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package plugin.lsttokens.equipment;

import pcgen.cdom.enumeration.EqModControl;
import pcgen.cdom.enumeration.ObjectKey;
import pcgen.core.Equipment;
import pcgen.rules.context.LoadContext;
import pcgen.rules.persistence.token.AbstractToken;
import pcgen.rules.persistence.token.CDOMPrimaryToken;
import pcgen.util.Logging;

/**
 * Deals with MODS token
 */
public class ModsToken extends AbstractToken implements
		CDOMPrimaryToken<Equipment>
{

	@Override
	public String getTokenName()
	{
		return "MODS";
	}

	public boolean parse(LoadContext context, Equipment eq, String value)
	{
		if (isEmpty(value))
		{
			return false;
		}
		EqModControl ctrl;
		try
		{
			ctrl = EqModControl.valueOf(value);
		}
		catch (IllegalArgumentException iae)
		{
			Logging.log(Logging.LST_ERROR, "Invalid Mod Control provided in "
					+ getTokenName() + ": " + value);
			if (value.length() == 0)
			{
				return false;
			}
			switch (value.charAt(0))
			{
			case 'R':
			case 'r':
				ctrl = EqModControl.REQUIRED;
				break;

			case 'Y':
			case 'y':
				ctrl = EqModControl.YES;
				break;

			case 'N':
			case 'n':
				ctrl = EqModControl.NO;
				break;

			default:
				return false;
			}
		}
		context.getObjectContext().put(eq, ObjectKey.MOD_CONTROL, ctrl);
		return true;
	}

	public String[] unparse(LoadContext context, Equipment eq)
	{
		EqModControl control = context.getObjectContext().getObject(eq,
				ObjectKey.MOD_CONTROL);
		if (control == null)
		{
			return null;
		}
		return new String[] { control.toString() };
	}

	public Class<Equipment> getTokenClass()
	{
		return Equipment.class;
	}
}
